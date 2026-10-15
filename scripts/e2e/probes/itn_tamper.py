import sys; sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__))); from lib import *
import time; run=str(int(time.time()))[-6:]; slug=f'a2z-tamper-{run}'
s,h,b=req('POST','/api/v1/auth/register',{'firmName':'A2Z tamper','email':f'o@{slug}.co.za','password':'Sup3r-Secret-99','firstName':'A','lastName':'B','firmSlug':slug,'planSlug':'firm'})
tok=j(b)['data']['accessToken']
c=j(req('POST','/api/v1/product-billing/seats/checkout',{'seats':3,'staffSeats':1},tok=tok)[2])['data']; f=c.get('fields') or c.get('form'); f=list(f.items()) if isinstance(f,dict) else [(x['name'],x['value']) for x in f]; g=dict(f)
def itn(amount, pfid, extra=()):
    base=[('m_payment_id',g['m_payment_id']),('pf_payment_id',pfid),('payment_status','COMPLETE'),('amount_gross',amount),('merchant_id',g['merchant_id']),('token','tok-'+pfid)]+list(extra)
    r=req('POST','/api/v1/product-billing/payfast/itn',raw=urllib.parse.urlencode(base+[('signature',pfsign(base))]).encode(),ctype='application/x-www-form-urlencoded')
    st=j(req('GET','/api/v1/product-billing',tok=tok)[2])['data']
    return r[0], r[2][:100], st.get('seatStatus'), st.get('seatCount')
print('expected amount', g['amount'])
print('tampered 1.00 first :', itn('1.00','T1'+run))
print('unsigned/bad sig     :', req('POST','/api/v1/product-billing/payfast/itn',raw=urllib.parse.urlencode([('m_payment_id',g['m_payment_id']),('amount_gross',g['amount']),('payment_status','COMPLETE'),('merchant_id',g['merchant_id']),('signature','00')]).encode(),ctype='application/x-www-form-urlencoded')[:1])
print('correct             :', itn(g['amount'],'T2'+run))
print('status keys', list(j(req('GET','/api/v1/product-billing',tok=tok)[2])['data'].keys()))
