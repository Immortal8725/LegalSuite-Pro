import json, os, urllib.request, urllib.parse, hashlib, uuid, os
B = os.environ.get('API', 'http://127.0.0.1:18291')
ENV = dict(l.strip().split('=',1) for l in open(os.environ.get('API_ENV','../e2e-api.env')) if '=' in l)
def req(m, p, body=None, tok=None, headers=None, raw=None, ctype='application/json', base=None):
    h = dict(headers or {})
    if tok: h['Authorization'] = 'Bearer ' + tok
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    if data is not None and 'Content-Type' not in h: h['Content-Type'] = ctype
    r = urllib.request.Request((base or B) + p, data=data, method=m, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=60) as x: return x.status, dict(x.headers), x.read()
    except urllib.error.HTTPError as e: return e.code, dict(e.headers), e.read()
    except Exception as e: return -1, {}, ('CONN:'+str(e)).encode()
def j(b):
    try: return json.loads(b)
    except Exception: return {'raw': b[:200].decode('latin1')}
def login(slug, email, pw='password'):
    s,h,b = req('POST','/api/v1/auth/login',{'firmSlug':slug,'email':email,'password':pw})
    return (j(b).get('data') or {}).get('accessToken')
def pfsign(fields):
    s = '&'.join(f"{k}={urllib.parse.quote_plus(str(v).strip())}" for k,v in fields if k!='signature' and str(v)!='')
    pp = ENV.get('PAYFAST_PASSPHRASE','')
    if pp: s += '&passphrase=' + urllib.parse.quote_plus(pp.strip())
    return hashlib.md5(s.encode()).hexdigest()
def multipart(fields, files):
    bd = uuid.uuid4().hex; out = b''
    for k,v in fields.items(): out += f'--{bd}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
    for k,(fn,data,ct) in files.items(): out += f'--{bd}\r\nContent-Disposition: form-data; name="{k}"; filename="{fn}"\r\nContent-Type: {ct}\r\n\r\n'.encode() + data + b'\r\n'
    return out + f'--{bd}--\r\n'.encode(), 'multipart/form-data; boundary=' + bd
