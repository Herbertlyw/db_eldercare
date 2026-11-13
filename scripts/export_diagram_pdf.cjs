// Chromium prints the SVG as vectors with embedded CJK glyphs, not a screenshot.
const fs=require('fs');
const {chromium}=require('/Users/herbert/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{const b=await chromium.launch({channel:'chrome',headless:true});const p=await b.newPage();
const src=fs.readFileSync('docs/diagrams/global-uml.svg','utf8');const match=src.match(/viewBox="[^"]*?([\d.]+) ([\d.]+)"/);const [,w,h]=match;
const font=fs.readFileSync('.local/fonts/NotoSansCJKsc-Regular.otf').toString('base64');
await p.setContent(`<style>@font-face{font-family:'Noto Sans CJK SC';src:url(data:font/otf;base64,${font})}html,body{margin:0}svg{width:100%;height:100%;display:block}</style>${src}`);
await p.evaluate(()=>document.fonts.ready);await p.pdf({path:'docs/diagrams/全局UML类图_可放大.pdf',width:((Number(w)+2)/72)+'in',height:((Number(h)+2)/72)+'in',printBackground:true,margin:{top:0,bottom:0,left:0,right:0}});await b.close();console.log('Vector PDF',w,h);})();
