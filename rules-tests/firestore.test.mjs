import { readFileSync } from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, setDoc, getDoc, getDocs, updateDoc, deleteDoc } from 'firebase/firestore';
let env;
const play = (uid = 'alice', numeros = [5,12,18,23,34,41]) => ({uid, numeros});
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
 const db=own();for(const id of ['cerrado','final','missing']) await assertFails(setDoc(doc(db,`sorteos/${id}/jugadas/alice`),play()));
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
