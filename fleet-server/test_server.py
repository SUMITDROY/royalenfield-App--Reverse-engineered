import tempfile,time,unittest
from pathlib import Path
from server import Fleet

class Tests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.f=Fleet(Path(self.temp.name)/'db',{'a'*32:'admin','b'*32:'r1','c'*32:'r2','d'*32:'outsider'})
 def tearDown(self):self.temp.cleanup()
 def location(self,user):return self.f.execute(user,'POST','/api/location',{'lat':12.5,'lon':77.5,'accuracy':10,'time':int(time.time()*1000),'ride':1})
 def test_auth_and_roles(self):
  self.assertEqual(self.f.identity('b'*32),'r1')
  with self.assertRaises(PermissionError):self.f.identity('bad')
  with self.assertRaises(PermissionError):self.f.execute('r1','GET','/api/dashboard')
  with self.assertRaises(PermissionError):self.f.execute('r1','POST','/api/assign',{})
 def test_assignment_and_group_isolation(self):
  for u in ['r1','r2']:self.f.execute('admin','POST','/api/assign',{'user':u,'title':'Ride','destination':'Meet','group':'group'})
  self.location('r2')
  self.assertEqual(len(self.f.execute('r1','GET','/api/assignments')['positions']),1)
  self.assertEqual(self.f.execute('outsider','GET','/api/assignments'),{'routes':[],'positions':[]})
 def test_location_validation_retention_deletion(self):
  self.location('r1')
  with self.assertRaises(ValueError):self.location('r1')
  with self.assertRaises(ValueError):self.f.execute('r2','POST','/api/location',{'lat':float('nan')})
  self.assertEqual(len(self.f.execute('admin','GET','/api/dashboard')['locations']),1)
  self.f.execute('r1','POST','/api/delete-my-data',{})
  self.assertEqual(self.f.execute('admin','GET','/api/dashboard')['locations'],[])
  self.location('r2')
  with self.f.db() as d:d.execute('UPDATE locations SET received=0')
  self.assertEqual(self.f.execute('admin','GET','/api/dashboard')['locations'],[])

class HTTPTests(unittest.TestCase):
 def test_transport_auth_and_manager_boundary(self):
  from server import Handler,ThreadingHTTPServer
  import threading,urllib.request,urllib.error,json
  with tempfile.TemporaryDirectory() as folder:
   server=ThreadingHTTPServer(('127.0.0.1',0),Handler);server.fleet=Fleet(Path(folder)/'db',{'a'*32:'admin','b'*32:'r1'})
   thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
   try:
    base='http://127.0.0.1:'+str(server.server_port)
    with urllib.request.urlopen(base+'/') as response:
     self.assertIn('Tripper fleet manager',response.read().decode());self.assertEqual(response.headers['Cache-Control'],'no-store')
    for token in ['', 'b'*32]:
     req=urllib.request.Request(base+'/api/dashboard',headers={'Authorization':'Bearer '+token})
     with self.assertRaises(urllib.error.HTTPError) as e:urllib.request.urlopen(req)
     self.assertEqual(e.exception.code,403);e.exception.close()
    req=urllib.request.Request(base+'/api/dashboard',headers={'Authorization':'Bearer '+'a'*32})
    with urllib.request.urlopen(req) as response:self.assertEqual(json.load(response)['riders'],['r1'])
   finally:server.shutdown();server.server_close();thread.join()

if __name__=='__main__':unittest.main()
