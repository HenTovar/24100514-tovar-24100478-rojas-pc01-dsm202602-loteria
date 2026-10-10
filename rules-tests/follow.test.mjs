import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test,before,after} from 'node:test';
import {initializeTestEnvironment} from '@firebase/rules-unit-testing';
import {doc,collection,setDoc,updateDoc,onSnapshot,query,orderBy} from 'firebase/firestore';
let env;
before(async()=>{
 if(process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:8080')throw new Error('Only disposable local emulator');
 env=await initializeTestEnvironment({projectId:'demo-loteria-follow',firestore:{host:'127.0.0.1',port:8080,rules:readFileSync(new URL('../firestore.rules',import.meta.url),'utf8')}});
 await env.clearFirestore();
});
after(async()=>{await env?.cleanup();});
// Keep one subscription per source for the whole test, like the Android screen.
function watch(ref) {
 let latest,error;const waiters=new Set();
 const notify=()=>{for(const w of [...waiters])if(error || (latest && w.predicate(latest))){clearTimeout(w.timer);waiters.delete(w);error?w.reject(error):w.resolve(latest);}};
 const remove=onSnapshot(ref,s=>{latest=s;notify();},e=>{error=e;notify();});
 return {
  wait(predicate){return new Promise((resolve,reject)=>{const w={predicate,resolve,reject,timer:setTimeout(()=>{waiters.delete(w);reject(new Error('snapshot timeout'));},10000)};waiters.add(w);notify();});},
  close(){remove();for(const w of waiters){clearTimeout(w.timer);w.reject(new Error('listener closed'));}waiters.clear();}
 };
}
test('two real listeners receive header changes and ordered balls',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();await setDoc(doc(db,'sorteos/prueba'),{estado:'abierto'});
  await setDoc(doc(db,'sorteos/prueba/bolillas/first'),{orden:1,numero:5});
 });
 const db=env.authenticatedContext('alice').firestore();
 const header=watch(doc(db,'sorteos/prueba'));
 const balls=watch(query(collection(db,'sorteos/prueba/bolillas'),orderBy('orden','asc')));
 try {
  await header.wait(s=>s.data()?.estado==='abierto');await balls.wait(s=>s.size===1);
  await env.withSecurityRulesDisabled(async c=>{
   const admin=c.firestore();await updateDoc(doc(admin,'sorteos/prueba'),{estado:'en_curso'});
   await setDoc(doc(admin,'sorteos/prueba/bolillas/third'),{orden:3,numero:12});
   await setDoc(doc(admin,'sorteos/prueba/bolillas/second'),{orden:2,numero:41});
  });
  assert.equal((await header.wait(s=>s.data()?.estado==='en_curso')).data().estado,'en_curso');
  assert.deepEqual((await balls.wait(s=>s.size===3)).docs.map(d=>d.data().numero),[5,41,12]);
 } finally {header.close();balls.close();}
});
test('anonymous realtime listener is rejected',async()=>{
 const db=env.unauthenticatedContext().firestore();const listener=watch(doc(db,'sorteos/prueba'));
 try {await assert.rejects(listener.wait(()=>true),e=>e.code==='permission-denied');}finally{listener.close();}
});
