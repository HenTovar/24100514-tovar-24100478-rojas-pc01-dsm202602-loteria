import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, setDoc, getDoc, getDocs, updateDoc, deleteDoc, runTransaction } from 'firebase/firestore';
let env;
const play = (uid = 'alice', numeros = [5,12,18,23,34,41]) => ({sorteoId:'abierto', uid, numeros});
before(async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  if (host !== '127.0.0.1:8080') throw new Error('Only local disposable emulator accepted');
  env = await initializeTestEnvironment({projectId:'demo-loteria-pc01',firestore:{host:'127.0.0.1',port:8080,rules:readFileSync(new URL('../firestore.rules',import.meta.url),'utf8')}});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c => {
    const db=c.firestore();
    await setDoc(doc(db,'sorteos/abierto'),{nombre:'Prueba',estado:'abierto'});
    await setDoc(doc(db,'sorteos/cerrado'),{estado:'en_curso'});
    await setDoc(doc(db,'sorteos/final'),{estado:'finalizado'});
    await setDoc(doc(db,'sorteos/abierto/bolillas/1'),{numero:5,orden:1});
    await setDoc(doc(db,'sorteos/abierto/jugadas/bob'),play('bob'));
  });
});
const own = () => env.authenticatedContext('alice').firestore();
test('anonymous cannot read or write',async()=>{
 const db=env.unauthenticatedContext().firestore();
 await assertFails(getDoc(doc(db,'sorteos/abierto')));
 await assertFails(setDoc(doc(db,'sorteos/abierto/jugadas/alice'),play()));
});
test('authenticated reads header and published balls',async()=>{
 const db=own();await assertSucceeds(getDoc(doc(db,'sorteos/abierto')));await assertSucceeds(getDoc(doc(db,'sorteos/abierto/bolillas/1')));
});
test('own valid play accepted and readable',async()=>{
 const db=own();const ref=doc(db,'sorteos/abierto/jugadas/alice');await assertSucceeds(setDoc(ref,play()));await assertSucceeds(getDoc(ref));
});
test('cannot write another uid or forge uid field',async()=>{
 const db=own();await assertFails(setDoc(doc(db,'sorteos/abierto/jugadas/charlie'),play('charlie')));await assertFails(setDoc(doc(db,'sorteos/abierto/jugadas/alice'),play('bob')));
});
test('cannot read or list other plays',async()=>{
 const db=own();await assertFails(getDoc(doc(db,'sorteos/abierto/jugadas/bob')));await assertFails(getDocs(collection(db,'sorteos/abierto/jugadas')));
});
test('cannot overwrite or delete a play even while open',async()=>{
 const db=own();const ref=doc(db,'sorteos/abierto/jugadas/alice');await assertSucceeds(setDoc(ref,play()));await assertFails(updateDoc(ref,{numeros:[1,2,3,4,5,6]}));await assertFails(deleteDoc(ref));await assertFails(setDoc(ref,play()));
});
test('only open existing draw accepts play',async()=>{
 const db=own();for(const id of ['cerrado','final','missing']) await assertFails(setDoc(doc(db,`sorteos/${id}/jugadas/alice`),{...play(),sorteoId:id}));
});
test('invalid numbers rejected',async()=>{
 const db=own();for(const ns of [[1,2,3,4,5],[1,2,3,4,5,5],[0,2,3,4,5,6],[1,2,3,4,5,46],[1,2,3,4,5,6.5],[1,2,3,4,5,'6'],[1,2,3,4,5,6,7]]) await assertFails(setDoc(doc(db,'sorteos/abierto/jugadas/alice'),play('alice',ns)));
});
test('cannot persist result or extra fields',async()=>{
 await assertFails(setDoc(doc(own(),'sorteos/abierto/jugadas/alice'),{...play(),resultado:'GANADOR'}));
});
test('client cannot create edit or delete headers or balls',async()=>{
 const db=own();await assertFails(setDoc(doc(db,'sorteos/nuevo'),{estado:'abierto'}));await assertFails(updateDoc(doc(db,'sorteos/abierto'),{estado:'finalizado'}));await assertFails(deleteDoc(doc(db,'sorteos/abierto')));await assertFails(setDoc(doc(db,'sorteos/abierto/bolillas/2'),{numero:12,orden:2}));await assertFails(deleteDoc(doc(db,'sorteos/abierto/bolillas/1')));
});
test('unknown collection denied',async()=>{
 await assertFails(setDoc(doc(own(),'usuarios/alice'),{admin:true}));await assertFails(getDoc(doc(own(),'usuarios/alice')));
});

test('draw id must be present and match document path',async()=>{
 const db=own();const ref=doc(db,'sorteos/abierto/jugadas/alice');
 const {sorteoId,...missing}=play();await assertFails(setDoc(ref,missing));
 await assertFails(setDoc(ref,{...play(),sorteoId:'otro'}));
});
test('transaction rejects existing play and persists exact payload once',async()=>{
 const db=own();const header=doc(db,'sorteos/abierto');const ref=doc(db,'sorteos/abierto/jugadas/alice');
 const save=()=>runTransaction(db,async tx=>{
   const d=await tx.get(header);const old=await tx.get(ref);
   if(d.data().estado!=='abierto'||old.exists())throw new Error('reject');
   tx.set(ref,play());
 });
 await save();const stored=(await getDoc(ref)).data();
 if(JSON.stringify(stored.numeros)!==JSON.stringify(play().numeros)||stored.sorteoId!=='abierto'||stored.uid!=='alice')throw new Error('payload mismatch');
 await assert.rejects(save(), /^Error: reject$/);
 assert.deepEqual((await getDoc(ref)).data(),stored);
});
test('concurrent creations leave exactly one immutable play',async()=>{
 const db=own();const ref=doc(db,'sorteos/abierto/jugadas/alice');
 const results=await Promise.allSettled([setDoc(ref,play()),setDoc(ref,play('alice',[1,2,3,4,5,6]))]);
 if(results.filter(r=>r.status==='fulfilled').length!==1)throw new Error('expected exactly one creation');
 await assertFails(updateDoc(ref,{numeros:[7,8,9,10,11,12]}));
});

test('closing draw between transaction read and commit prevents play',async()=>{
 const db=own();const header=doc(db,'sorteos/abierto');const ref=doc(db,'sorteos/abierto/jugadas/alice');let closed=false;
 await assertFails(runTransaction(db,async tx=>{
   const d=await tx.get(header);const old=await tx.get(ref);
   if(!closed){closed=true;await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'sorteos/abierto'),{estado:'en_curso'}));}
   if(d.data().estado!=='abierto'||old.exists())throw new Error('reject');
   tx.set(ref,play());
 }));
 const result=await getDoc(ref);if(result.exists())throw new Error('closed draw accepted a play');
});
