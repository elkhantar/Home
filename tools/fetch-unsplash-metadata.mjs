import fs from 'node:fs/promises';
import path from 'node:path';

// Metadata only: no photo downloads, account writes or HTML scraping.
const root=path.resolve(import.meta.dirname,'..');
const dir=path.join(root,'wallpaper-metadata');
const inventory=JSON.parse(await fs.readFile(path.join(dir,'inventory.json'),'utf8'));
const keyFile=path.join(root,'.unsplash-access-key');
const cacheDir=path.join(dir,'cache');
const quotaFile=path.join(dir,'quota.json');
await fs.mkdir(cacheDir,{recursive:true});
const text=v=>typeof v==='string'?v.replace(/[\u0000-\u001f\u007f]/g,' ').trim():'';
const short=v=>{const words=text(v).split(/\s+/).filter(Boolean);return {value:words.slice(0,20).join(' ')+(words.length>20?'…':''),truncated:words.length>20};};
async function readJson(file,fallback=null){try{return JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code==='ENOENT')return fallback;throw e;}}
async function atomic(file,value){const temp=file+'.tmp';await fs.writeFile(temp,JSON.stringify(value,null,2)+'\n','utf8');await fs.rename(temp,file);}
function normalize(p,id){
 if(p.id!==id || !p.user?.name)throw new Error('API response lacks the matching photo identity or photographer');
 const caption=short(p.description);
 return {schema_version:2,id,status:'verified',photographer:text(p.user.name),photographer_username:text(p.user.username),
  photographer_url:text(p.user.links?.html).split('?')[0],photo_url:text(p.links?.html).split('?')[0] || `https://unsplash.com/photos/${id}`,
  attribution:`Photo by ${text(p.user.name)} on Unsplash`,
  date_published:p.created_at || null,date_taken:null,date_source:'Unsplash created_at (publication date)',
  photographer_caption:caption.value || null,photographer_caption_truncated:caption.truncated,
  location:text(p.location?.name) || [p.location?.city,p.location?.country].map(text).filter(Boolean).join(', '),
  location_city:text(p.location?.city),location_country:text(p.location?.country),
  original_width:p.width,original_height:p.height,license_url:'https://unsplash.com/license',
  source:'Unsplash API /photos/:id',fetched_at:new Date().toISOString()};
}
async function exportCatalogue(){
 const photos=[];
 for(const file of inventory){
  let m=await readJson(path.join(cacheDir,file.id+'.json'));
  if(m){m={...m};delete m.unsplash_alt_description;if(m.description_source==='unsplash_alt_description'){delete m.description;delete m.description_source;delete m.description_truncated;}}
  const slug=file.filename.replace(new RegExp('-'+file.id+'-unsplash.*$'),'');
  photos.push({...file,relative_path:file.collection+'/'+file.filename,filename_author_slug:slug,
   ...(m || {status:'pending',photo_url:`https://unsplash.com/photos/${file.id}`,photographer:null,description:null,location:null}),
   status:m?.status==='verified' && m.schema_version!==2?'needs_enrichment':m?.status || 'pending',
   caption:m?.schema_version===2 && m.status==='verified'?[m.photographer_caption,m.location,m.attribution].filter(Boolean).join(' \u00b7 '):null});
 }
 const counts={};for(const p of photos)counts[p.status]=(counts[p.status]||0)+1;
 const result={schema_version:2,generated_at:new Date().toISOString(),summary:counts,photos};
 await atomic(path.join(dir,'catalogue.json'),result);
 const columns=['collection','filename','id','status','photographer','date_published','date_taken','photographer_caption','photographer_caption_truncated','location','attribution','photo_url','photographer_url'];
 const csv=v=>{let s=String(v??'');if(/^[=+\-@]/.test(s))s="'"+s;return '"'+s.replaceAll('"','""')+'"';};
 await fs.writeFile(path.join(dir,'catalogue.csv'),'\uFEFF'+[columns.map(csv).join(','),...photos.map(p=>columns.map(k=>csv(p[k])).join(','))].join('\r\n')+'\r\n');
 const missing=photos.filter(p=>p.status!=='verified').map(p=>({collection:p.collection,filename:p.filename,id:p.id,status:p.status,photo_url:p.photo_url}));
 await atomic(path.join(dir,'pending.json'),missing);
 return counts;
}
if(process.argv.includes('--catalogue-only')){console.log(JSON.stringify(await exportCatalogue()));process.exit(0);}
const key=(process.env.UNSPLASH_ACCESS_KEY || await fs.readFile(keyFile,'utf8')).replace(/^\uFEFF/,'').trim();
if(!key || /\s/.test(key))throw new Error('Access-key file is empty or contains whitespace. Save just the Access Key.');
const quota=await readJson(quotaFile,{});
if(quota.resume_after && Date.parse(quota.resume_after)>Date.now()){
 console.log('Hourly allowance exhausted. Safe resume after '+quota.resume_after);
 console.log(JSON.stringify(await exportCatalogue()));process.exit(0);
}
let fetched=0,stopped=null;
for(const file of inventory){
 const cached=await readJson(path.join(cacheDir,file.id+'.json'));
 if(cached && (cached.status==='unavailable' || cached.schema_version===2))continue;
 let response;
 try{response=await fetch(`https://api.unsplash.com/photos/${encodeURIComponent(file.id)}`,{
  headers:{Authorization:'Client-ID '+key,'Accept-Version':'v1'},redirect:'error',signal:AbortSignal.timeout(20000)});}
 catch(e){stopped='Connection failed; completed results are saved. Re-run to retry.';break;}
 const remainingHeader=response.headers.get('x-ratelimit-remaining');
 const remaining=remainingHeader===null?null:Number(remainingHeader);
 const limit=Number(response.headers.get('x-ratelimit-limit')) || null;
 const quotaState={checked_at:new Date().toISOString(),limit,remaining};
 if(response.status===401){stopped='Access Key rejected (401). Check the Access Key, not Secret Key.';await response.body?.cancel();break;}
 if(response.status===403 || response.status===429 || remaining===0){
  quotaState.resume_after=new Date(Date.now()+Math.max(3600,Number(response.headers.get('retry-after'))||0)*1000).toISOString();
 }
 if(response.ok){
  const body=await response.text();if(body.length>512*1024)throw new Error('Unexpectedly large metadata response');
  await atomic(path.join(cacheDir,file.id+'.json'),normalize(JSON.parse(body),file.id));fetched++;
 }else if(response.status===404 || response.status===410){
  await response.body?.cancel();
  await atomic(path.join(cacheDir,file.id+'.json'),{id:file.id,status:'unavailable',http_status:response.status,
   photo_url:`https://unsplash.com/photos/${file.id}`,checked_at:new Date().toISOString()});
 }else{
  await response.body?.cancel();stopped=`API returned ${response.status}; no retries attempted in this batch.`;
 }
 await atomic(quotaFile,quotaState);
 if(fetched===1 || fetched%10===0)console.log(`Fetched ${fetched} this batch; API remaining: ${remaining??'unknown'} / ${limit??'unknown'}`);
 if(stopped || remaining===0){stopped ||= 'Hourly allowance reached. Resume after '+quotaState.resume_after;break;}
 await new Promise(resolve=>setTimeout(resolve,1100));
}
console.log(JSON.stringify({fetched_this_batch:fetched,summary:await exportCatalogue(),stopped},null,2));
