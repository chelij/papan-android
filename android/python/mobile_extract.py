import contextlib
import html.parser
import json
import os
from pathlib import Path
import sys
import urllib.parse
import urllib.request
import subprocess

sys.path.insert(0, str(Path(__file__).with_name('packages.zip')))
import desktop_extract as desktop

desktop.MAX_FILE = 32 * 1024 * 1024


class Page(html.parser.HTMLParser):
    def __init__(self, url):
        super().__init__(convert_charrefs=True)
        self.base, self.title, self.in_title = url, '', False
        self.meta, self.items = {}, []

    def handle_starttag(self, tag, attributes):
        values = dict(attributes)
        if tag == 'base' and values.get('href'):
            self.base = urllib.parse.urljoin(self.base, values['href'])
        if tag == 'meta':
            self.meta[values.get('property', values.get('name', '')).lower()] = values.get('content', '')
        if tag == 'title':
            self.in_title = True
        if tag in ('img', 'video', 'source') and values.get('src'):
            self.items.append((values['src'], 'image' if tag == 'img' else 'video', values.get('poster')))

    def handle_endtag(self, tag):
        if tag == 'title':
            self.in_title = False

    def handle_data(self, data):
        if self.in_title:
            self.title += data


def page_inspect(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 Papan/0.3', 'Accept-Encoding': 'identity'})
    with urllib.request.urlopen(request, timeout=15) as response:
        kind = response.headers.get_content_type().split('/')[0]
        if kind in ('image', 'video'):
            return {'title': Path(urllib.parse.urlsplit(url).path).name or 'Media', 'items': [{'key': 'original', 'kind': kind, 'url': response.url}], 'engine': 'direct'}
        document = response.read(1024 * 1024 + 1)
        if len(document) > 1024 * 1024:
            raise ValueError('The page is too large to inspect.')
        page = Page(response.url)
        page.feed(document.decode('utf-8', errors='replace'))
    values = [(page.meta[key], 'video', page.meta.get('og:image')) for key in ('og:video:secure_url', 'og:video:url', 'og:video', 'twitter:player:stream') if page.meta.get(key)]
    values += page.items
    values += [(page.meta[key], 'image', None) for key in ('og:image', 'twitter:image') if page.meta.get(key)]
    items, seen = [], set()
    for value, kind, poster in values:
        address = urllib.parse.urljoin(page.base, value)
        try:
            desktop.web_url(address)
        except ValueError:
            continue
        if address in seen:
            continue
        seen.add(address)
        items.append({'key': desktop.media_key({}, address, len(items)), 'kind': kind, 'url': address,
                      'poster': urllib.parse.urljoin(page.base, poster) if poster else (address if kind == 'image' else None)})
        if len(items) == 50:
            break
    if not items:
        raise ValueError('No downloadable images or videos were found. You can save the link instead.')
    return {'title': page.meta.get('og:title') or page.title or url, 'items': items, 'engine': 'direct'}


def inspect(request):
    url = request['url']
    host = urllib.parse.urlsplit(url).hostname.lower().removeprefix('www.')
    if request.get('engine') in ('gallery', 'instagram', 'video'):
        engines = [request['engine']]
    elif request.get('engine') == 'direct':
        return page_inspect(url)
    elif host in ('instagram.com', 'm.instagram.com', 'x.com', 'twitter.com', 'mobile.twitter.com'):
        engines = ['gallery', 'instagram', 'video'] if 'instagram' in host else ['gallery', 'video']
    elif host in ('youtube.com', 'm.youtube.com', 'youtu.be', 'turbo.cr', 'vod.sooplive.com'):
        engines = ['video']
    else:
        return page_inspect(url)
    failure = None
    for engine in engines:
        try:
            if engine == 'gallery':
                result = desktop.gallery_inspect(url)
            elif engine == 'instagram':
                result = desktop.instagram_inspect(url)
            else:
                result = desktop.video_inspect(request)
            if not result.get('items'):
                raise ValueError('No downloadable media was found.')
            return dict(result, engine=engine)
        except Exception as error:
            failure = error
    raise ValueError(str(failure))


def download(request):
    if request['engine'] != 'direct':
        engine = request['engine']
        if engine == 'gallery':
            results = desktop.gallery_download(request)
        elif engine == 'instagram':
            results = desktop.instagram_download(request)
        elif engine == 'video':
            results = desktop.video_download(request)
        else:
            raise ValueError('Unknown media provider.')
        for result in results:
            file = Path(result['file'])
            if file.suffix.lower() == '.jpeg':
                target = file.with_suffix('.jpg')
                file.rename(target)
                result['file'] = str(target)
            elif file.suffix.lower() in ('.mkv', '.mov') and next((item['kind'] for item in request['items'] if item['key'] == result['key']), None) == 'video':
                target = file.with_suffix('.mp4')
                subprocess.run([request['ffmpeg'], '-nostdin', '-y', '-i', str(file), '-c', 'copy', str(target)], check=True, capture_output=True, timeout=45)
                result['file'] = str(target)
        return results
    item = request['item']
    address = desktop.web_url(item['url'])
    headers = {'User-Agent': 'Mozilla/5.0 Papan/0.3'}
    page, target = urllib.parse.urlsplit(request['url']), urllib.parse.urlsplit(address)
    if not (page.scheme == 'https' and target.scheme == 'http'):
        headers['Referer'] = urllib.parse.urlunsplit(page._replace(fragment='')) if (page.scheme, page.netloc.lower()) == (target.scheme, target.netloc.lower()) else f'{page.scheme}://{page.netloc}/'
    with urllib.request.urlopen(urllib.request.Request(address, headers=headers), timeout=15) as response:
        media_type = response.headers.get_content_type()
        extensions = {'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp', 'image/gif': '.gif', 'video/mp4': '.mp4', 'video/webm': '.webm'}
        extension = extensions.get(media_type)
        if not extension or not media_type.startswith(item['kind'] + '/'):
            raise ValueError('The source returned a page or poster instead of the selected media.')
        target = Path(request['output']) / ('media' + extension)
        length = 0
        with target.open('wb') as saved:
            while data := response.read(65536):
                length += len(data)
                if length > desktop.MAX_FILE:
                    raise ValueError('The selected media exceeds 32 MiB.')
                saved.write(data)
        if not length:
            raise ValueError('The source returned an empty file.')
    return [{'key': item['key'], 'file': str(target.resolve())}]


try:
    request = json.loads(sys.stdin.read(1024 * 1024))
    request['url'] = desktop.web_url(request['url'])
    with contextlib.redirect_stdout(sys.stderr):
        result = inspect(request) if request['action'] == 'inspect' else download(request)
    print(json.dumps({'ok': True, 'result': result}))
except Exception as error:
    print(json.dumps({'ok': False, 'error': str(error).replace('512 MiB', '32 MiB')[:1200]}))
    sys.exit(1)
