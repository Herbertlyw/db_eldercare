const fs=require('fs');const runtime='/Users/herbert/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';const {instance}=require(runtime+'/@viz-js/viz');const sharp=require(runtime+'/sharp');
(async()=>{const viz=await instance(),dir='docs/diagrams/';
for(const f of fs.readdirSync(dir).filter(x=>x.endsWith('-uml.dot')))fs.writeFileSync(dir+f.replace('.dot','.svg'),viz.renderString(fs.readFileSync(dir+f,'utf8'),{format:'svg',engine:'dot'}));
for(const f of fs.readdirSync(dir).filter(x=>x.endsWith('.svg')))await sharp(dir+f,{density:180,limitInputPixels:false}).resize({width:f==='global-uml.svg'?6500:2400}).png().toFile(dir+f.replace('.svg','.png'));
console.log('Rendered report figures');})();
