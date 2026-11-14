import {DatabaseSync} from 'node:sqlite';import fs from 'node:fs';
export function database(filename=':memory:'){
 const db=new DatabaseSync(filename);for(const f of fs.readdirSync(new URL('../drizzle/',import.meta.url)).filter(x=>x.endsWith('.sql')))db.exec(fs.readFileSync(new URL('../drizzle/'+f,import.meta.url),'utf8'));
 return {
  raw:db,
  prepare(sql){
   return {bind(...args){
    const statement=db.prepare(sql);
    return {async first(){return statement.get(...args)||null},async run(){return statement.run(...args)},async all(){return {results:statement.all(...args)}}};
   }};
  }
 };
}
