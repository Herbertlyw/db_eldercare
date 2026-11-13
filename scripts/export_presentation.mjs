import fs from 'node:fs/promises';
import {pathToFileURL,fileURLToPath} from 'node:url';
const R=fileURLToPath(new URL('..',import.meta.url)).replace(/\/$/,'');
const modules='/Users/herbert/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES ??= modules;
const skill='/Users/herbert/.codex/plugins/cache/openai-primary-runtime/presentations/26.1111.10934/skills/presentations';
const {Presentation,PresentationFile}=await import(pathToFileURL(modules+'/@oai/artifact-tool/dist/artifact_tool.mjs'));
const {editDeck}=await import('./academic_presentation.mjs');
const {finalizePresentation}=await import(pathToFileURL(skill+'/container_tools/artifact_tool_utils.mjs'));
await fs.mkdir(R+'/.local/presentation-finalizer',{recursive:true});
const p=Presentation.create({slideSize:{width:1280,height:720}});
for(let i=0;i<13;i++)p.slides.add();
const tests=JSON.parse(await fs.readFile(R+'/evidence/test-summary.json','utf8'));const browser=JSON.parse(await fs.readFile(R+'/evidence/completion-browser.json','utf8'));
const assets={crest:await fs.readFile(R+'/docs/assets/课程模板校徽.png'),workspace:await fs.readFile(R+'/evidence/ui-workspace-completion.png')};
await editDeck({presentation:p,input:{slides:[0,1,2,3,4,5,6,7,8,9,10,11,12],assets,testCount:tests.tests,readRoutes:browser.checks.reduce((n,x)=>n+x.readRoutes,0),browserActions:browser.actionsDone.length}});
p.slides.getItem(12).moveTo(3);
for(let i=0;i<p.slides.items.length;i++)for(const shape of p.slides.getItem(i).shapes.items){
 const pos=shape.position;
 if(pos?.left===58&&pos?.top===44)shape.text=String(i).padStart(2,'0');
 if(pos?.left===1160&&pos?.top===675)shape.text=String(i+1).padStart(2,'0');
}
const candidate=R+'/.local/presentation-finalizer/candidate.pptx';
await (await PresentationFile.exportPptx(p)).save(candidate);
await fs.mkdir(R+'/.local/validated-slides',{recursive:true});
const validated=R+'/.local/validated-slides/validated-'+Date.now()+'.pptx';
const result=await finalizePresentation({workspaceDir:R,candidatePath:candidate,finalPath:validated,pythonExecutable:'/Users/herbert/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3',integrityValidatorPath:skill+'/container_tools/inspect_presentation_package_integrity.py',layoutValidatorPath:skill+'/container_tools/inspect_presentation_layout_geometry.py',layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit',...([2,9,11,12].flatMap(n=>['--require-native-table-slide',String(n)]))],fontPolicy:{basis:'design',families:['Microsoft YaHei','宋体','Courier New']},requiredNativeTableOwnerSlides:[2,9,11,12],verifyArtifactToolImport:true,receiptPath:R+'/.local/presentation-finalizer/validation-'+Date.now()+'.json'});
await fs.copyFile(validated,R+'/outputs/社区居家养老平台课程答辩_修订版.pptx');
console.log(JSON.stringify(result));
