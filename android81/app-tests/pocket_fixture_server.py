"""Loopback-only synthetic HTTP/SSH/SFTP fixtures. Never executes received shell commands."""
from pathlib import Path
import socket,threading,time,io,wave,os,json
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
import paramiko
ROOT=Path(__file__).resolve().parent.parent/'output/pocket-fixture-server';ROOT.mkdir(exist_ok=True)
(ROOT/'download.txt').write_text('LEO SFTP FIXTURE: șț🙂\n',encoding='utf-8')
b=io.BytesIO()
with wave.open(b,'wb') as w:w.setparams((1,2,22050,22050,'NONE','not compressed'));w.writeframes(b'\0\0'*22050)
WAV=b.getvalue()
RSS=b"<?xml version='1.0'?><rss version='2.0'><channel><title>Local fixture</title><item><guid>fixture-episode</guid><title>LEO LOCAL EPISODE</title><description><![CDATA[<b>Synthetic</b> only.]]></description><link>/article</link><enclosure url='http://127.0.0.1:18774/episode.wav' type='audio/wav'/></item></channel></rss>"
class HTTP(BaseHTTPRequestHandler):
 def do_GET(self):
  if self.path=='/redirect':self.send_response(302);self.send_header('Location','/feed.xml');self.end_headers();return
  if self.path=='/feed.xml':data=RSS;typ='application/rss+xml'
  elif self.path=='/episode.wav':data=WAV;typ='audio/wav'
  elif self.path=='/icy':
   title=b"StreamTitle='LEO TEST TRACK';";data=b'X'*1024+bytes([(len(title)+15)//16])+title.ljust(((len(title)+15)//16)*16,b'\0');typ='audio/mpeg'
  else:self.send_error(404);return
  self.send_response(200);self.send_header('Content-Type',typ);self.send_header('Content-Length',str(len(data)))
  if self.path=='/icy':self.send_header('icy-metaint','1024')
  self.end_headers()
  try:self.wfile.write(data)
  except (BrokenPipeError,ConnectionResetError):pass
 def log_message(self,*args):pass
class Files(paramiko.SFTPServerInterface):
 def path(self,path):
  candidate=(ROOT/path.lstrip('/')).resolve()
  if candidate!=ROOT.resolve() and ROOT.resolve() not in candidate.parents:raise OSError('Path outside fixture')
  return candidate
 def canonicalize(self,path):return '/'+str(self.path(path).relative_to(ROOT.resolve())).replace('\\','/').strip('.')
 def list_folder(self,path):
  try:
   out=[]
   for p in self.path(path).iterdir():
    a=paramiko.SFTPAttributes.from_stat(p.stat());a.filename=p.name;out.append(a)
   return out
  except OSError:return paramiko.SFTP_NO_SUCH_FILE
 def stat(self,path):
  try:return paramiko.SFTPAttributes.from_stat(self.path(path).stat())
  except OSError:return paramiko.SFTP_NO_SUCH_FILE
 lstat=stat
 def open(self,path,flags,attr):
  try:
   p=self.path(path);f=open(p,'wb' if flags&os.O_WRONLY or flags&os.O_RDWR else 'rb');h=paramiko.SFTPHandle(flags);h.readfile=f;h.writefile=f;return h
  except OSError:return paramiko.SFTP_PERMISSION_DENIED
class SSH(paramiko.ServerInterface):
 def check_auth_password(self,u,p):return paramiko.AUTH_SUCCESSFUL if u=='leo-fixture' and p=='local-fixture-only' else paramiko.AUTH_FAILED
 def get_allowed_auths(self,u):return 'password'
 def check_channel_request(self,kind,chan):return paramiko.OPEN_SUCCEEDED if kind=='session' else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED
 def check_channel_pty_request(self,*args):return True
 def check_channel_shell_request(self,channel):
  def console():
   channel.send(b'LEO SSH FIXTURE READY\n');buf=b''
   try:
    while True:
     data=channel.recv(4096)
     if not data:break
     buf+=data
     if b'\n' in buf:
      line,buf=buf.split(b'\n',1);channel.send(b'LEO COMMAND OK: '+line+b'\n')
   except Exception:pass
  threading.Thread(target=console,daemon=True).start();return True
key=paramiko.RSAKey.generate(2048)
def clients(server):
 while True:
  sock,_=server.accept()
  def run(client):
   transport=paramiko.Transport(client);transport.add_server_key(key);transport.set_subsystem_handler('sftp',paramiko.SFTPServer,Files)
   try:
    transport.start_server(server=SSH())
    while transport.is_active():time.sleep(.2)
   except Exception:pass
   finally:transport.close()
  threading.Thread(target=run,args=(sock,),daemon=True).start()
server=socket.socket();server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);server.bind(('127.0.0.1',2267));server.listen(8);threading.Thread(target=clients,args=(server,),daemon=True).start()
print('Local HTTP 18774 and SSH/SFTP 2267 ready',flush=True)
ThreadingHTTPServer(('127.0.0.1',18774),HTTP).serve_forever()
