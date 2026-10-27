import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..'),dir=path.join(root,'wallpaper-metadata');
const statusFile=path.join(dir,'collector-status.json');
async function read(file,fallback={}){try{return JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code==='ENOENT')return fallback;throw e;}}
async function status(s){await fs.writeFile(statusFile,JSON.stringify({updated_at:new Date().toISOString(),pid:process.pid,...s},null,2));}
function run(exe,args){return new Promise(resolve=>{const child=spawn(exe,args,{cwd:root,windowsHide:true,stdio:['ignore','inherit','inherit']});child.on('error',()=>resolve(-1));child.on('exit',code=>resolve(code));});}
while(true){
 let catalogue=await read(path.join(dir,'catalogue.json'),{summary:{pending:366}});
 let pending=(catalogue.summary.pending||0)+(catalogue.summary.needs_enrichment||0);
 if(!pending){
  const synced=await run('python',['tools/publish-wallpaper-catalogue.py','--push']);
  if(synced===2){await status({state:'waiting_for_tv',summary:catalogue.summary});await new Promise(resolve=>setTimeout(resolve,300000));continue;}
  await status({state:synced===0?'complete':'stopped_on_error',summary:catalogue.summary});break;
 }
 const quota=await read(path.join(dir,'quota.json'));
 const until=Date.parse(quota.resume_after||'')||0;
 if(until>Date.now()){
  await status({state:'waiting_for_api_allowance',next_batch_at:quota.resume_after,summary:catalogue.summary});
  await new Promise(resolve=>setTimeout(resolve,Math.min(60000,until-Date.now()+1000)));continue;
 }
 await status({state:'fetching',summary:catalogue.summary});
 const result=await run(process.execPath,['tools/fetch-unsplash-metadata.mjs']);
 if(result!==0){await status({state:'stopped_on_error',reason:'Metadata collector failed; see collector-errors.log'});break;}
 catalogue=await read(path.join(dir,'catalogue.json'));
 const left=(catalogue.summary.pending||0)+(catalogue.summary.needs_enrichment||0);
 if(left>=pending){await status({state:'stopped_on_error',reason:'No metadata progress; review API key or connection.'});break;}
 const publish=await run('python',['tools/publish-wallpaper-catalogue.py','--push']);
 if(publish!==0)console.log('Metadata saved; TV publication will be retried after the next batch.');
 await status({state:left?'batch_complete':'complete',summary:catalogue.summary});
}
