"""Run only against an isolated local backend/database; creates a test product."""
import io
import json
import os
import sys
import time
import uuid
from pathlib import Path
import requests
from PIL import Image
from test_ai import fixture

base=os.getenv("TEST_BACKEND_URL","http://127.0.0.1:18088")
if not base.startswith("http://127.0.0.1:"):
    raise SystemExit("This test is restricted to an isolated loopback backend")
session=requests.Session()


def call(method,path,**kwargs):
    response=session.request(method,base+path,timeout=150,**kwargs)
    if not response.ok:
        raise RuntimeError(f"{method} {path}: HTTP {response.status_code}: {response.text[:500]}")
    return response.json() if response.content else None


def wait_job(job):
    until=time.monotonic()+150
    while job["status"] not in ["FAILED","COMPLETED"] and time.monotonic()<until:
        time.sleep(0.5)
        job=call("GET",f'/api/v1/products/images/jobs/{job["id"]}')
    assert job["status"]=="COMPLETED",job.get("message")
    return job


if __name__=="__main__":
    login=call("POST","/api/v1/auth/login",json={"email":os.environ["TEST_ADMIN_EMAIL"],"password":os.environ["TEST_ADMIN_PASSWORD"]})
    session.headers["Authorization"]="Bearer "+login["token"]
    categories=call("GET","/api/v1/admin/categories")
    product=call("POST","/api/v1/admin/products",json={"sku":"STUDIO-TEST-"+uuid.uuid4().hex[:10],"name":"Studio validation phone", "categoryId":categories[0]["id"],"price":1000,"active":False,"images":[]})
    _,source=fixture("phone")
    asset=call("POST","/api/v1/products/images/upload",files={"file":("phone.png",source,"image/png")},data={"productId":product["id"]})
    started=time.perf_counter()
    job=call("POST","/api/v1/products/images/process",json={"assetId":asset["id"],"productId":product["id"],"background":"white","format":"webp","width":800,"height":800})
    enqueue=time.perf_counter()-started
    assert job["status"] in ["PENDING","PROCESSING"],"Enqueue should return before inference completes"
    assert enqueue<5
    job=wait_job(job)
    duration=time.perf_counter()-started
    result=job["asset"]
    assert result["model"]=="isnet-general-use" and result["format"]=="webp"
    exported=session.get(base+result["masterUrl"],timeout=10); exported.raise_for_status()
    output=Image.open(io.BytesIO(exported.content))
    assert output.size==(800,800) and output.format=="WEBP"
    assert min(output.getpixel((0,0))[:3])>=250
    original=session.get(base+f'/api/v1/products/images/{asset["id"]}/original',timeout=10)
    assert original.content==source
    assert requests.get(base+f'/api/v1/products/images/{asset["id"]}/original',timeout=10).status_code in [401,403]
    call("POST",f'/api/v1/products/images/{asset["id"]}/confirm',json={"productId":product["id"],"main":True})
    saved=call("GET",f'/api/v1/admin/products/{product["id"]}')
    assert saved["mainImageUrl"]==result["masterUrl"]
    assert result["masterUrl"] in saved["images"]
    batch_assets=[call("POST","/api/v1/products/images/upload",files={"file":("phone.png",source,"image/png")},data={"productId":product["id"]}) for _ in range(2)]
    batch_start=time.perf_counter()
    batch=call("POST","/api/v1/products/images/batch-process",json={"images":[{"assetId":a["id"],"productId":product["id"],"background":"transparent","format":"png","width":400,"height":400} for a in batch_assets]})
    batch_enqueue=time.perf_counter()-batch_start
    assert batch_enqueue<5 and len(batch)==2 and all(r["job"] for r in batch)
    for row in batch:
        complete=wait_job(row["job"])
        png=session.get(base+complete["asset"]["masterUrl"],timeout=10)
        alpha=Image.open(io.BytesIO(png.content))
        assert alpha.format=="PNG" and alpha.getchannel("A").getextrema()[0]<255
    report={"enqueue_seconds":round(enqueue,3),"end_to_end_seconds":round(duration,3),"bytes":len(exported.content),"product_id":product["id"],"asset_id":asset["id"],"original_preserved":True,"anonymous_original_denied":True,"persisted_in_product":True,
            "batch_count":2,"batch_enqueue_seconds":round(batch_enqueue,3),"batch_end_to_end_seconds":round(time.perf_counter()-batch_start,3),"batch_png_alpha_verified":True}
    target=Path(__file__).resolve().parents[2]/".studio-test-output"
    target.mkdir(exist_ok=True)
    (target/"integration.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print(json.dumps(report,indent=2))
