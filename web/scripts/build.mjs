import fs from 'node:fs/promises';import path from 'node:path';import {build} from 'esbuild';
await fs.mkdir('dist/server',{recursive:true});await fs.mkdir('dist/.openai',{recursive:true});
await fs.cp('public','dist/client',{recursive:true});
await fs.copyFile('.openai/hosting.json','dist/.openai/hosting.json');
await build({entryPoints:['worker/index.js'],outfile:'dist/server/index.js',bundle:true,format:'esm',platform:'browser',target:'es2022',minify:true,alias:{'node:crypto':path.resolve('lib/crypto.js')}});
console.log('Built Worker and public 3D assets');
