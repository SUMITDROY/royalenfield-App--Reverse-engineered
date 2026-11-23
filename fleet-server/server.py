#!/usr/bin/env python3
"""Small self-hosted fleet relay. Bind loopback; terminate trusted HTTPS at a reverse proxy."""
import argparse
import hashlib
import hmac
import json
import math
import os
from pathlib import Path
import secrets
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = Path(__file__).resolve().parent

class Fleet:
    def __init__(self, database, tokens, retention_days=7):
        if not tokens or len(set(tokens.values())) != len(tokens) or any(len(k) < 24 for k in tokens):
            raise ValueError('Unique users and tokens of at least 24 characters are required')
        if 'admin' not in tokens.values():
            raise ValueError('An admin credential is required')
        self.database, self.tokens = str(database), tokens
        if not 1 <= retention_days <= 365:
            raise ValueError('Retention must be 1..365 days')
        self.retention = retention_days * 86400 * 1000
        with self.db() as d:
            d.executescript('''
                CREATE TABLE IF NOT EXISTS locations(user TEXT,time INTEGER,received INTEGER,lat REAL,lon REAL,accuracy REAL,ride INTEGER);
                CREATE INDEX IF NOT EXISTS location_user ON locations(user,time);
                CREATE TABLE IF NOT EXISTS routes(id INTEGER PRIMARY KEY,user TEXT,title TEXT,destination TEXT,notes TEXT,group_name TEXT);
                CREATE TABLE IF NOT EXISTS groups_members(group_name TEXT,user TEXT,PRIMARY KEY(group_name,user));
            ''')
        if Path(database).exists():
            Path(database).chmod(0o600)

    def db(self):
        return sqlite3.connect(self.database, timeout=15)

    def identity(self, bearer):
        for key, user in self.tokens.items():
            if hmac.compare_digest(hashlib.sha256(key.encode()).digest(), hashlib.sha256(bearer.encode()).digest()):
                return user
        raise PermissionError('Unauthorized')

    def execute(self, user, method, path, body=None):
        now = int(time.time() * 1000)
        with self.db() as d:
            d.execute('DELETE FROM locations WHERE received < ?', (now - self.retention,))
            if method == 'POST' and path == '/api/location' and user != 'admin':
                if not isinstance(body, dict):
                    raise ValueError('Object required')
                for k, low, high in [('lat', -90, 90), ('lon', -180, 180), ('accuracy', 0, 10000)]:
                    v = body.get(k)
                    if isinstance(v, bool) or not isinstance(v, (float, int)) or not math.isfinite(v) or not low <= v <= high:
                        raise ValueError('Invalid coordinates/accuracy')
                stamp, ride = body.get('time'), body.get('ride')
                if type(stamp) is not int or abs(stamp-now) > 300000 or type(ride) is not int or ride <= 0:
                    raise ValueError('Invalid time/ride')
                last = d.execute('SELECT MAX(received) FROM locations WHERE user=?', (user,)).fetchone()[0]
                if last and now-last < 5000:
                    raise ValueError('Update limit: once per five seconds')
                d.execute('INSERT INTO locations VALUES(?,?,?,?,?,?,?)', (user, stamp, now, body['lat'], body['lon'], body['accuracy'], ride))
                return {'ok': True}
            if method == 'POST' and path == '/api/delete-my-data' and user != 'admin':
                d.execute('DELETE FROM locations WHERE user=?', (user,))
                d.execute('DELETE FROM routes WHERE user=?', (user,))
                d.execute('DELETE FROM groups_members WHERE user=?', (user,))
                return {'ok': True}
            if method == 'GET' and path == '/api/assignments' and user != 'admin':
                rows = d.execute('SELECT id,title,destination,notes,group_name FROM routes WHERE user=? ORDER BY id DESC LIMIT 100', (user,)).fetchall()
                peers = d.execute('''SELECT DISTINCT a.user FROM groups_members a JOIN groups_members b
                                    ON a.group_name=b.group_name WHERE b.user=? AND a.user != ?''', (user,user)).fetchall()
                positions=[]
                for (peer,) in peers:
                    row=d.execute('SELECT time,lat,lon,accuracy FROM locations WHERE user=? ORDER BY received DESC LIMIT 1',(peer,)).fetchone()
                    if row and now-row[0]<120000:
                        positions.append(dict(zip(['user','time','lat','lon','accuracy'],[peer,*row])))
                return {'routes':[dict(zip(['id','title','destination','notes','group'],r)) for r in rows], 'positions':positions}
            if user != 'admin':
                raise PermissionError('Manager role required')
            if method == 'GET' and path == '/api/dashboard':
                locations = d.execute('SELECT user,time,lat,lon,accuracy,ride FROM locations ORDER BY received DESC LIMIT 1000').fetchall()
                rides = d.execute('SELECT user,ride,MIN(time),MAX(time),COUNT(*) FROM locations GROUP BY user,ride ORDER BY MAX(time) DESC LIMIT 200').fetchall()
                routes = d.execute('SELECT id,user,title,destination,notes,group_name FROM routes ORDER BY id DESC LIMIT 200').fetchall()
                return {'locations':[dict(zip(['user','time','lat','lon','accuracy','ride'],r)) for r in locations],
                        'rides':[dict(zip(['user','ride','first','last','points'],r)) for r in rides],
                        'routes':[dict(zip(['id','user','title','destination','notes','group'],r)) for r in routes],
                        'riders':[u for u in self.tokens.values() if u!='admin']}
            if method == 'POST' and path == '/api/assign':
                rider=body.get('user');title=body.get('title');destination=body.get('destination');notes=body.get('notes','');group=body.get('group','')
                if rider not in self.tokens.values() or rider=='admin':
                    raise ValueError('Unknown rider')
                if not all(isinstance(x,str) and len(x)<=1000 for x in [title,destination,notes,group]) or not title or not destination:
                    raise ValueError('Title/destination required, fields limited to 1000 characters')
                count=d.execute('SELECT COUNT(*) FROM routes WHERE user=?',(rider,)).fetchone()[0]
                if count>=1000:raise ValueError('Delete older assignments first')
                d.execute('INSERT INTO routes(user,title,destination,notes,group_name) VALUES(?,?,?,?,?)',(rider,title,destination,notes,group))
                if group:d.execute('INSERT OR IGNORE INTO groups_members VALUES(?,?)',(group,rider))
                return {'ok':True}
            if method=='POST' and path=='/api/delete-rider-data':
                rider=body.get('user')
                if rider not in self.tokens.values() or rider=='admin':raise ValueError('Unknown rider')
                for table in ['locations','routes','groups_members']:d.execute('DELETE FROM '+table+' WHERE user=?',(rider,))
                return {'ok':True}
            if method=='POST' and path=='/api/delete-assignment':
                ident=body.get('id')
                if type(ident) is not int:raise ValueError('Invalid assignment')
                d.execute('DELETE FROM routes WHERE id=?',(ident,));return {'ok':True}
            raise ValueError('Unknown endpoint')

class Handler(BaseHTTPRequestHandler):
    server_version='TripperFleet/0.1'
    def setup(self):
        super().setup();self.connection.settimeout(15)
    def log_message(self,*args):pass  # No tokens, routes or GPS in access logs.
    def reply(self,status,data,mime='application/json'):
        raw=data if isinstance(data,bytes) else json.dumps(data,allow_nan=False).encode()
        self.send_response(status)
        for k,v in {'Content-Type':mime,'Content-Length':str(len(raw)),'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'"}.items():self.send_header(k,v)
        self.end_headers();self.wfile.write(raw)
    def dispatch(self):
        try:
            if self.command=='GET' and self.path in ['/','/dashboard.js','/style.css']:
                name,mime={'/':('index.html','text/html; charset=utf-8'),'/dashboard.js':('dashboard.js','text/javascript'),'/style.css':('style.css','text/css')}[self.path]
                self.reply(200,(HERE/name).read_bytes(),mime);return
            auth=self.headers.get('Authorization','')
            if not auth.startswith('Bearer '):raise PermissionError('Unauthorized')
            user=self.server.fleet.identity(auth[7:]);body=None
            if self.command=='POST':
                length=int(self.headers.get('Content-Length','0'))
                if not 0<length<=16384:raise ValueError('Body must be 1..16384 bytes')
                body=json.loads(self.rfile.read(length))
                if not isinstance(body,dict):raise ValueError('JSON object required')
            self.reply(200,self.server.fleet.execute(user,self.command,self.path,body))
        except PermissionError:self.reply(403,{'error':'Unauthorized or forbidden'})
        except (ValueError,TypeError,KeyError,json.JSONDecodeError):self.reply(400,{'error':'Invalid request'})
        except Exception:self.reply(500,{'error':'Internal error'})
    do_GET=dispatch
    do_POST=dispatch

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--init',action='store_true');p.add_argument('--config',default='.local/fleet-users.json');p.add_argument('--db',default='.local/fleet.sqlite');p.add_argument('--port',type=int,default=8787);p.add_argument('--retention-days',type=int,default=7);a=p.parse_args()
    config=Path(a.config)
    if a.init:
        config.parent.mkdir(parents=True,exist_ok=True);config.parent.chmod(0o700)
        with config.open('x') as f:json.dump({secrets.token_urlsafe(32):'admin',secrets.token_urlsafe(32):'rider-1'},f,indent=2)
        config.chmod(0o600);print('Created private credentials at '+str(config)+'. Share rider token only with its rider.');return
    tokens=json.loads(config.read_text());Path(a.db).parent.mkdir(parents=True,exist_ok=True)
    server=ThreadingHTTPServer(('127.0.0.1',a.port),Handler);server.fleet=Fleet(a.db,tokens,a.retention_days)
    print('Fleet listening on loopback. A trusted HTTPS reverse proxy is required for phones.');server.serve_forever()
if __name__=='__main__':main()
