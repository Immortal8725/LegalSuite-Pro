import sys, base64, struct, hmac, hashlib, time; sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__))); from lib import *
OUT=[]
def rec(area,name,ok,detail=''): OUT.append(dict(area=area,name=name,ok=bool(ok),detail=str(detail)[:300])); print(('PASS' if ok else 'FAIL'),area,'|',name,'|',str(detail)[:220])
def totp(sec,off=0):
    k=base64.b32decode(sec.upper()+'='*((8-len(sec)%8)%8)); c=struct.pack('>Q',int(time.time())//30+off); h=hmac.new(k,c,hashlib.sha1).digest(); o=h[-1]&15
    return '%06d'%((struct.unpack('>I',h[o:o+4])[0]&0x7fffffff)%1000000)
run=str(int(time.time()))[-6:]; slug='a2z-2fa-'+run; pw='Sup3r-Secret-99'
b=j(req('POST','/api/v1/auth/register',{'firmName':'A2Z 2FA','email':f'o@{slug}.co.za','password':pw,'firstName':'A','lastName':'B','firmSlug':slug,'planSlug':'firm'})[2]); T=b['data']['accessToken']
st=j(req('POST','/api/v1/auth/totp/start',tok=T)[2])['data']; sec=st['secret']
rec('2FA','Wrong code does not enable',req('POST','/api/v1/auth/totp/confirm',{'code':'000000'},tok=T)[0]>=400)
s,h,bb=req('POST','/api/v1/auth/totp/confirm',{'code':totp(sec)},tok=T); rec('2FA','Correct code enables',s==200,(s,bb[:100]))
s,h,bb=req('POST','/api/v1/auth/login',{'firmSlug':slug,'email':f'o@{slug}.co.za','password':pw}); d=j(bb).get('data') or {}
rec('2FA','Password alone no longer yields a token',not d.get('accessToken'),(s,str(d)[:120]))
s,h,bb=req('POST','/api/v1/auth/login',{'firmSlug':slug,'email':f'o@{slug}.co.za','password':pw,'totpCode':'123456'}); rec('2FA','Wrong TOTP refused',s>=400 or not (j(bb).get('data') or {}).get('accessToken'),s)
s,h,bb=req('POST','/api/v1/auth/login',{'firmSlug':slug,'email':f'o@{slug}.co.za','password':pw,'totpCode':totp(sec)}); T2=(j(bb).get('data') or {}).get('accessToken'); rec('2FA','Password + TOTP signs in',bool(T2),s)
code=totp(sec); s1=req('POST','/api/v1/auth/login',{'firmSlug':slug,'email':f'o@{slug}.co.za','password':pw,'totpCode':code})[0]
rec('2FA','Same TOTP code cannot be used twice (replay guard)', s1==401, s1)
s=req('POST','/api/v1/auth/totp/disable',{},tok=T2)[0]; rec('2FA','Disable needs password/code',s>=400,s)
# roles
A=login('smith-associates','maria@smithlaw.com'); AS=login('smith-associates','alex@smithlaw.com')
for who,tok in [('attorney(maria)',A),('associate(alex)',AS)]:
    r={}
    r['usersList']=req('GET','/api/v1/users',tok=tok)[0]
    r['inviteUser']=req('POST','/api/v1/users/invite',{'email':f'x{run}@e.co','role':'owner','firstName':'X','lastName':'Y'},tok=tok)[0]
    r['branding']=req('PUT','/api/v1/firm/branding',{'firmName':'Hacked'},tok=tok)[0]
    r['productBillingCheckout']=req('POST','/api/v1/product-billing/seats/checkout',{'seats':1},tok=tok)[0]
    r['tenantModules']=req('PUT','/api/v1/tenant/modules/expenses',{'enabled':False},tok=tok)[0]
    r['publicSiteSubmit']=req('POST','/api/v1/public-site/submit',{},tok=tok)[0]
    r['operatorQueue']=req('GET','/api/v1/operator/approvals',tok=tok)[0]
    ok=all(v in(401,403,404,405) for k,v in r.items() if k not in('usersList',))
    rec('Roles',f'{who} blocked from firm admin endpoints',ok,r)
json.dump(OUT,open(__import__('os').environ.get('RESULTS_DIR','.')+'/results-2fa-roles.json','w'),indent=1)
