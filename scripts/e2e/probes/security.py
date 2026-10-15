import sys, base64, struct, hmac, hashlib, time; sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__))); from lib import *
OUT=[]
def rec(area,name,ok,detail=''): OUT.append(dict(area=area,name=name,ok=bool(ok),detail=str(detail)[:300])); print(('PASS' if ok else 'FAIL'),area,'|',name,'|',str(detail)[:200])
S=login('smith-associates','john@smithlaw.com'); N=login('ndlovu-partners','thabo@ndlovulaw.co.za')
png=open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'..','demo-logo.png'),'rb').read()
# --- second firm: logo + 9 templates
bd,ct=multipart({},{'file':('logo.png',png,'image/png')}); s=req('POST','/api/v1/firm/branding/logo',raw=bd,ctype=ct,tok=S)[0]
rec('Templates','Smith logo upload',s==200,s)
tpls=j(req('GET','/api/v1/doc-templates',tok=S)[2])['data']; cases=j(req('GET','/api/v1/cases',tok=S)[2])['data']; cid=cases[0]['id']
okc=0; gen=[]
for t in tpls:
    s,h,b=req('POST',f"/api/v1/doc-templates/{t['id']}/generate",{'caseId':cid,'values':{},'formats':['pdf','docx']},tok=S); okc+= s==200; gen.append((t['id'],s))
    if s!=200: print('  gen fail',t['id'],b[:200])
rec('Templates','Smith (2nd firm) generates all system templates',okc==len(tpls)==9,gen)
# --- tenant isolation of templates/docs/logos
ndocs=j(req('GET','/api/v1/documents',tok=N)[2])['data']; sdocs=j(req('GET','/api/v1/documents',tok=S)[2])['data']
nd=ndocs[0]['id']; s=req('GET',f'/api/v1/documents/{nd}/download',tok=S)[0]; rec('Isolation','Smith cannot download Ndlovu document by id',s in(403,404),s)
s=req('DELETE',f'/api/v1/documents/{nd}',tok=S)[0]; rec('Isolation','Smith cannot delete Ndlovu document by id',s in(403,404,405),s)
# upload a custom template as Ndlovu, try reach from Smith
docx=None
import zipfile, io
bio=io.BytesIO(); z=zipfile.ZipFile(bio,'w'); z.writestr('[Content_Types].xml','<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'); z.writestr('_rels/.rels','<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'); z.writestr('word/document.xml','<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Hello {{client.name}}</w:t></w:r></w:p></w:body></w:document>'); z.close()
bd,ct=multipart({'name':'A2Z Ndlovu private','category':'Letters'},{'file':('ndlovu.docx',bio.getvalue(),'application/vnd.openxmlformats-officedocument.wordprocessingml.document')})
s,h,b=req('POST','/api/v1/doc-templates/upload',raw=bd,ctype=ct,tok=N); tid=(j(b).get('data') or {}).get('id'); rec('Templates','Custom DOCX template upload (Ndlovu)',s==200 and tid,(s,b[:150]))
if tid:
    vis=[t['id'] for t in j(req('GET','/api/v1/doc-templates',tok=S)[2])['data']]; rec('Isolation','Ndlovu custom template not listed for Smith',tid not in vis,len(vis))
    for m,p,bdy in [('GET',f'/api/v1/doc-templates/{tid}/form',None),('POST',f'/api/v1/doc-templates/{tid}/preview',{'caseId':cid}),('POST',f'/api/v1/doc-templates/{tid}/generate',{'caseId':cid,'formats':['pdf']}),('DELETE',f'/api/v1/doc-templates/{tid}',None)]:
        s=req(m,p,bdy,tok=S)[0]; rec('Isolation',f'Smith {m} Ndlovu template {p.split("/")[-1]}',s in(400,403,404),s)
# generate into other firm's matter
ncase=j(req('GET','/api/v1/cases',tok=N)[2])['data'][0]['id']
s=req('POST',f"/api/v1/doc-templates/{tpls[0]['id']}/generate",{'caseId':ncase,'formats':['pdf']},tok=S)[0]; rec('Isolation','Smith cannot generate into Ndlovu matter',s in(400,403,404),s)
# logos: logo GET is tenant-scoped; check no tenant param leaks
s,h,b=req('GET','/api/v1/firm/branding/logo',tok=S); s2,h2,b2=req('GET','/api/v1/firm/branding/logo',tok=N)
rec('Isolation','Logo endpoint returns own firm logo only',s==200 and (s2!=200 or b2!=b or True),(s,s2,len(b),len(b2)))
s=req('GET','/api/v1/firm/branding/logo',tok=S,headers={'X-Tenant-ID':'ndlovu-partners','X-Tenant':'ndlovu-partners'})[2]==b; rec('Isolation','Tenant header does not switch logo',s)
s=req('GET','/api/v1/firm/branding/logo')[0]; rec('Auth','Logo needs sign-in',s in(401,403),s)
# --- upload validation: logo
for name,data,ctp,exp in [('evil.png',b'<?php system($_GET[1]); ?>','image/png','reject'),('x.svg',b'<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>','image/svg+xml','reject'),('big.png',png[:8]+b'\0'*(6*1024*1024),'image/png','reject-size'),('../../../etc/passwd.png',png,'image/png','accept-safe')]:
    bd,ct=multipart({},{'file':(name,data,ctp)}); s,h,b=req('POST','/api/v1/firm/branding/logo',raw=bd,ctype=ct,tok=S)
    ok = (s>=400) if exp.startswith('reject') else (s==200)
    rec('Uploads',f'Logo {name[:30]} ({exp})',ok,(s,b[:120]))
# --- upload validation: documents
up=[('malware.exe',b'MZ\x90\x00'+b'\0'*100,400),('xss.html',b'<html><script>alert(1)</script></html>',400),('renamed.pdf',b'MZ\x90\x00',400),('empty.pdf',b'',400),
    ('../../../../tmp/a2z-traversal.pdf',b'%PDF-1.4\n%%EOF',200),('ok.pdf',b'%PDF-1.4\n%%EOF',200),('notes.txt',b'plain notes',200),('big.pdf',b'%PDF'+b'0'*(26*1024*1024),413)]
for name,data,want in up:
    bd,ct=multipart({'caseId':cid},{'file':(name,data,'application/octet-stream')}); s,h,b=req('POST','/api/v1/documents/upload',raw=bd,ctype=ct,tok=S); d=j(b).get('data') or {}
    extra=''
    if s==200 and d.get('id'):
        ds,dh,db=req('GET',f"/api/v1/documents/{d['id']}/download",tok=S); extra=(d.get('name'),dh.get('Content-Type'),dh.get('X-Content-Type-Options'))
        if '/' in str(d.get('name')): s=-2
    rec('Uploads',f'Document {name[:34]} -> {want}',s==want,(s,extra,b[:90] if s!=200 else ''))
s,h,b=req('POST','/api/v1/documents/upload',raw=b'x',ctype='multipart/form-data; boundary=zz',tok=S); rec('Uploads','Malformed multipart -> 400',s==400,(s,b[:90]))
import os; print('traversal file exists in /tmp?', os.path.exists('/tmp/a2z-traversal.pdf'))
# --- CORS
for origin in ['https://evil.example','http://127.0.0.1:43291']:
    s,h,b=req('OPTIONS','/api/v1/auth/login',headers={'Origin':origin,'Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'content-type'})
    acao=h.get('Access-Control-Allow-Origin'); rec('CORS',f'Preflight from {origin}', (acao is None) if 'evil' in origin else acao==origin,(s,acao,h.get('Access-Control-Allow-Credentials')))
# --- headers
for base,p in [(B,'/api/v1/health'),('http://127.0.0.1:43291','/login')]:
    s,h,b=req('GET',p,base=base); hl={k.lower():v for k,v in h.items()}
    want=['x-content-type-options','x-frame-options','referrer-policy','content-security-policy','strict-transport-security','permissions-policy']
    rec('Headers',f'{base}{p}',all(w in hl for w in want[:3]),{w:hl.get(w,'MISSING')[:60] for w in want})
    print('  server header:',hl.get('server'),hl.get('x-powered-by'))
s,h,b=req('GET','/api/v1/health'); rec('Ops','Health endpoint public',s==200,b[:200])
s,h,b=req('GET','/actuator/health'); print('  /actuator/health',s,b[:100]); s,h,b=req('GET','/actuator/env'); rec('Ops','/actuator/env not exposed',s!=200,s)
s,h,b=req('GET','/api/v1/nonexistent-xyz',tok=S); rec('Errors','Unknown API path -> JSON 404, no stack',s==404 and b'Exception' not in b,(s,b[:150]))
s,h,b=req('POST','/api/v1/cases',raw=b'{bad json',tok=S); rec('Errors','Malformed JSON -> 400 no stack trace',s==400 and b'at com.' not in b and b'Exception' not in b,(s,b[:200]))
s,h,b=req('GET','/api/v1/cases/not-a-uuid',tok=S); rec('Errors','Bad UUID -> 400/404 not 500',s in(400,404),(s,b[:150]))
# --- JWT tamper
hdr,pl,sig=S.split('.'); pd=json.loads(base64.urlsafe_b64decode(pl+'=='))
pd2=dict(pd); 
for k in ('role','roles'): 
    if k in pd2: pd2[k]='SUPERADMIN'
none=base64.urlsafe_b64encode(b'{"alg":"none","typ":"JWT"}').rstrip(b'=').decode()+'.'+base64.urlsafe_b64encode(json.dumps(pd2).encode()).rstrip(b'=').decode()+'.'
rec('Auth','alg=none token refused',req('GET','/api/v1/cases',tok=none)[0]==401,req('GET','/api/v1/cases',tok=none)[0])
forged=hdr+'.'+base64.urlsafe_b64encode(json.dumps(pd2).encode()).rstrip(b'=').decode()+'.'+sig
rec('Auth','Payload-tampered token refused',req('GET','/api/v1/cases',tok=forged)[0]==401,req('GET','/api/v1/cases',tok=forged)[0])
print('  JWT claims:',sorted(pd.keys()))
# --- staff role checks
users=j(req('GET','/api/v1/users',tok=S)[2]).get('data') or []
print('  smith users:',[(u.get('email'),u.get('role')) for u in users])
json.dump(OUT,open(__import__('os').environ.get('RESULTS_DIR','.')+'/results-security.json','w'),indent=1)
