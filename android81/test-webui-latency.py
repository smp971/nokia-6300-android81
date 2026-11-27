"""Measure harmless Up-key receipts and screenshots on an idle Nokia. Password on stdin."""
import argparse
import hashlib
import http.client
import json
from pathlib import Path
import ssl
import statistics
import sys
import time
from urllib.parse import urlparse

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--identity',type=Path,required=True)
parser.add_argument('--phase',choices=['before','after'],required=True)
args=parser.parse_args()
password=sys.stdin.readline().rstrip('\r\n')
meta=json.loads(args.identity.read_text());url=urlparse(meta['endpoint']);pin=meta['fingerprint'].replace(':','').lower()
if url.scheme!='https':raise SystemExit('HTTPS required')
def request(method,path,data=None,token=None):
    connection=http.client.HTTPSConnection(url.hostname,url.port,context=ssl._create_unverified_context(),timeout=20)
    try:
        connection.connect()
        if hashlib.sha256(connection.sock.getpeercert(binary_form=True)).hexdigest()!=pin:raise RuntimeError('Certificate mismatch')
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        start=time.perf_counter();connection.request(method,path,json.dumps(data) if data is not None else None,headers);response=connection.getresponse();body=response.read();elapsed=round((time.perf_counter()-start)*1000,1)
        if response.status!=200:raise RuntimeError('Endpoint returned '+str(response.status))
        return body,elapsed
    finally:connection.close()
body,_=request('POST','/api/login',{'username':'admin','password':password});password='';token=json.loads(body)['token']
try:
    request('POST','/api/remote/key',{'key':'wake'},token);request('POST','/api/remote/key',{'key':'home'},token)
    time.sleep(1)  # Measure a settled Home window, not its launch transition.
    keys=[];frames=[]
    for _ in range(8):
        _,elapsed=request('POST','/api/remote/key',{'key':'up'},token);keys.append(elapsed)
        _,elapsed=request('GET','/api/remote/screen',token=token);frames.append(elapsed)
        time.sleep(.8)
    result={'key_ms':keys,'frame_ms':frames,'key_median_ms':statistics.median(keys),'frame_median_ms':statistics.median(frames),'includes_tls_handshake':False}
    output=Path(__file__).resolve().parent/'output/webui-fast';output.mkdir(exist_ok=True);(output/(args.phase+'.json')).write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))
finally:request('POST','/api/logout',{},token)
