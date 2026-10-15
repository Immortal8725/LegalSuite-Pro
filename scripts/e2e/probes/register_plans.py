import sys; sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__))); from lib import *
import time; R=[]; run=str(int(time.time()))[-6:]
for plan in ['solo','firm','practice']:
    slug=f'a2z-{plan}-{run}'
    s,h,b=req('POST','/api/v1/auth/register',{'firmName':f'A2Z {plan} {run}','email':f'owner@{slug}.co.za','password':'Sup3r-Secret-99','firstName':'Thandi','lastName':'Mokoena','firmSlug':slug,'planSlug':plan,'country':'ZA'})
    d=j(b); tok=(d.get('data') or {}).get('accessToken'); me=j(req('GET','/api/v1/auth/me',tok=tok)[2]).get('data') or {}
    st=j(req('GET','/api/v1/product-billing',tok=tok)[2]).get('data') or {}
    seats = 1 if plan=='solo' else 2; staff = 0 if plan=='solo' else 2
    cs,ch,cb=req('POST','/api/v1/product-billing/seats/checkout',{'seats':seats,'staffSeats':staff},tok=tok); c=j(cb)
    cd=c.get('data') or {}; flds=cd.get('fields') or cd.get('form') or {}
    if isinstance(flds,dict): flds=list(flds.items())
    else: flds=[(f.get('name'),f.get('value')) for f in flds]
    g=dict(flds); sigok = g.get('signature')==pfsign(flds)
    # solo with staff seats should be refused
    xs = req('POST','/api/v1/product-billing/seats/checkout',{'seats':2,'staffSeats':1},tok=tok)[0] if plan=='solo' else None
    base=[('m_payment_id',g.get('m_payment_id')),('pf_payment_id','A2Z'+run+plan),('payment_status','COMPLETE'),('amount_gross',g.get('amount')),('merchant_id',g.get('merchant_id')),('token','tok-a2z-'+plan+run)]
    ib=urllib.parse.urlencode(base+[('signature',pfsign(base))]).encode()
    i_s=req('POST','/api/v1/product-billing/payfast/itn',raw=ib,ctype='application/x-www-form-urlencoded')
    # replay
    i_r=req('POST','/api/v1/product-billing/payfast/itn',raw=ib,ctype='application/x-www-form-urlencoded')
    # tampered amount
    bad=[(k,('1.00' if k=='amount_gross' else v)) for k,v in base]; bb=urllib.parse.urlencode(bad+[('signature',pfsign(bad))]).encode()
    i_b=req('POST','/api/v1/product-billing/payfast/itn',raw=bb,ctype='application/x-www-form-urlencoded')
    st2=j(req('GET','/api/v1/product-billing',tok=tok)[2]).get('data') or {}
    R.append(dict(plan=plan,register=s,planOnMe=(me.get('tenant') or {}).get('plan') or me.get('planSlug') or me.get('plan'),checkout=cs,amount=g.get('amount'),item=g.get('item_name'),sigok=sigok,soloStaffRefused=xs,itn=i_s[0],itnBody=i_s[2][:80],replay=i_r[0],tampered=i_b[0],tamperedBody=i_b[2][:80],seatStatus=st2.get('seatStatus'),seatCount=st2.get('seatCount'),staffSeatCount=st2.get('staffSeatCount') or st2.get('staffSeats'),checkoutErr=None if cs==200 else cb[:200]))
for r in R: print(json.dumps(r,default=str))
