import subprocess, json, sys
def q(db, sql):
    out = subprocess.run(['sudo','-u','postgres','psql','-d',db,'-tA','-F','\t','-c',sql],capture_output=True,text=True,check=True).stdout
    return [l.split('\t') for l in out.splitlines() if l]
COLS = """select c.relname, a.attname, format_type(a.atttypid,a.atttypmod), a.attnotnull, coalesce(pg_get_expr(d.adbin,d.adrelid),'')
from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace
left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum
where n.nspname='public' and c.relkind='r' and a.attnum>0 and not a.attisdropped and c.relname<>'flyway_schema_history' order by 1,a.attnum"""
CONS = """select c.relname, k.conname, pg_get_constraintdef(k.oid) from pg_constraint k join pg_class c on c.oid=k.conrelid
join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname<>'flyway_schema_history' order by 1,2"""
IDX = """select tablename, indexname, indexdef from pg_indexes where schemaname='public' and tablename<>'flyway_schema_history'
and indexname not in (select conname from pg_constraint) order by 1,2"""
def load(db):
    cols={}
    for t,c,ty,nn,df in q(db,COLS): cols.setdefault(t,{})[c]=(ty,nn=='t',df)
    cons={(t,n):d for t,n,d in q(db,CONS)}
    idx={(t,n):d for t,n,d in q(db,IDX)}
    return cols,cons,idx
old, new = sys.argv[1], sys.argv[2]
oc,ok,oi = load(old); nc,nk,ni = load(new)
out=[]; notes=[]
DEFAULTS={'boolean':'false','integer':'0','bigint':'0','smallint':'0','double precision':'0','real':'0'}
for t in nc:
    if t not in oc:
        cols=[]
        for c,(ty,nn,df) in nc[t].items():
            cols.append(f'    {c} {ty}' + (f' default {df}' if df else '') + (' not null' if nn else ''))
        out.append(f'create table if not exists {t} (\n' + ',\n'.join(cols) + '\n);')
        continue
    for c,(ty,nn,df) in nc[t].items():
        if c not in oc[t]:
            dflt = df or (DEFAULTS.get(ty) if nn else None)
            if nn and dflt is None and ty.startswith('numeric'): dflt='0'
            if nn and dflt is None and ty.startswith('character varying'): dflt="''"
            s=f'alter table {t} add column if not exists {c} {ty}' + (f' default {dflt}' if dflt else '') + (' not null' if nn else '')
            out.append(s+';')
            if nn and not df: out.append(f'alter table {t} alter column {c} drop default;')
        else:
            oty,onn,odf = oc[t][c]
            if oty!=ty:
                out.append(f'alter table {t} alter column {c} type {ty};')
            if onn and not nn:
                out.append(f'alter table {t} alter column {c} drop not null;')
            if nn and not onn:
                notes.append(f'{t}.{c} is NOT NULL in the entity model but nullable on main; left nullable')
    for c in oc[t]:
        if c not in nc[t]: notes.append(f'{t}.{c} exists on main but not in the model; kept')
for t in oc:
    if t not in nc: notes.append(f'table {t} exists on main but not in the model; kept')
odefs={(t,d) for (t,n),d in ok.items()}
ndefs={(t,d) for (t,n),d in nk.items()}
for (t,n),d in nk.items():
    if (t,d) in odefs: continue
    out.append(f"do $$ begin if not exists (select 1 from pg_constraint k join pg_class c on c.oid=k.conrelid where c.relname='{t}' and pg_get_constraintdef(k.oid)='{d.replace(chr(39),chr(39)*2)}') then alter table {t} add constraint {n} {d}; end if; end $$;")
for (t,n),d in ok.items():
    if (t,d) not in ndefs: notes.append(f'constraint {n} on {t} exists on main but not in the model; kept')
for (t,n),d in ni.items():
    if (t,n) not in oi: out.append(d.replace('CREATE INDEX','CREATE INDEX IF NOT EXISTS').replace('CREATE UNIQUE INDEX','CREATE UNIQUE INDEX IF NOT EXISTS')+';')
print('\n'.join(out))
print('\n'.join('-- note: '+n for n in notes), file=sys.stderr)
