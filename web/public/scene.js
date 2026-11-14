import * as THREE from './vendor/three.module.js';
const S = window.Nostalife3D;
export function createScene(container) {
  const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
  renderer.setClearColor(0xa7c6d2);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  container.appendChild(renderer.domElement);
  const scene = new THREE.Scene(); scene.fog = new THREE.Fog(0xa7c6d2, 180, 950);
  const camera = new THREE.PerspectiveCamera(60, 1, .1, 1800);
  scene.add(new THREE.HemisphereLight(0xe4f4ff, 0x5b6e43, 2.4));
  const sun = new THREE.DirectionalLight(0xffe9be, 2.7); sun.position.set(170, 220, 80);
  scene.add(sun);
  const mat = (color, extra = {}) => new THREE.MeshStandardMaterial({ color, roughness: .75, ...extra });
  const materials = { grass: mat(0x82946b), road: mat(0x3b454b), white: mat(0xe5e7d8), red: mat(0xc65b42),
    metal: mat(0x697a7e), dark: mat(0x17242a), trunk: mat(0x6e6953), leaf: mat(0x426e55), blue: mat(0x83c6d8, { metalness: .25, roughness: .32 }) };
  function box(x, y, z, width, height, depth, material, parent = scene) {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(width, height, depth), material);
    mesh.position.set(x, y, z); parent.add(mesh); return mesh;
  }
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(1800, 1800), materials.grass);
  ground.rotation.x = -Math.PI / 2; ground.position.set(240, -.08, 160); scene.add(ground);
  function ribbon(offsetA, offsetB, material, lift = 0) {
    const positions = [], indices = [], segments = 480;
    for (let i = 0; i <= segments; i++) {
      const p = S.pointAt(i / segments * S.TRACK.length_m);
      for (const offset of [offsetA, offsetB]) {
        const x = p.x - Math.sin(p.heading) * offset, z = p.y + Math.cos(p.heading) * offset;
        positions.push(x, S.roadHeight(x, z) + lift, z);
      }
      if (i < segments) { const n = i * 2; indices.push(n, n + 2, n + 1, n + 1, n + 2, n + 3); }
    }
    const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3)); g.setIndex(indices); g.computeVertexNormals();
    const mesh = new THREE.Mesh(g, material); mesh.material.side = THREE.DoubleSide; scene.add(mesh);
  }
  const half = S.TRACK.width_m / 2;
  ribbon(-half, half, materials.road, .025);
  for (const sign of [-1, 1]) ribbon(sign * (half - .28), sign * (half - .05), materials.white, .04);
  const curbGeometry = new THREE.BoxGeometry(3, .12, 1.25);
  const curbCount = Math.floor(S.TRACK.length_m / 3), curb = new THREE.InstancedMesh(curbGeometry, materials.white, curbCount * 2);
  const dummy = new THREE.Object3D(), red = new THREE.Color(0xbd5842), white = new THREE.Color(0xe8e6d8);
  let instance = 0;
  for (let i = 0; i < curbCount; i++) for (const side of [-1, 1]) {
    const p = S.pointAt(i * S.TRACK.length_m / curbCount);
    dummy.position.set(p.x - Math.sin(p.heading) * side * (half + .6), .06, p.y + Math.cos(p.heading) * side * (half + .6));
    dummy.rotation.set(0, -p.heading, 0); dummy.updateMatrix(); curb.setMatrixAt(instance, dummy.matrix); curb.setColorAt(instance++, i % 2 ? white : red);
  }
  scene.add(curb);
  for (let i = 0; i < 12; i++) for (let j = 0; j < 2; j++)
    box(240 + j * .8, .055, 246 - half + i * S.TRACK.width_m / 12 + 1.2, .8, .025, 2.4, (i + j) % 2 ? materials.white : materials.dark);
  // Pit building, timing gantry, barriers, and a small low-poly landscape.
  box(290, 4, 295, 100, 8, 20, mat(0xc8d2cd)); box(290, 8.3, 295, 105, .6, 23, materials.dark);
  for (let i = 0; i < 10; i++) box(246 + i * 9.6, 3.2, 284.9, 7.8, 5.5, .2, materials.dark);
  box(240, 5.5, 229, .8, 11, .8, materials.metal); box(240, 5.5, 263, .8, 11, .8, materials.metal);
  box(240, 11, 246, 1.1, 2, 35, materials.dark);
  const banner = document.createElement('canvas'); banner.width = 1024; banner.height = 128;
  const bc = banner.getContext('2d'); bc.fillStyle = '#18232b'; bc.fillRect(0, 0, 1024, 128);
  bc.fillStyle = '#d4f799'; bc.font = 'bold 62px Arial'; bc.textAlign = 'center'; bc.fillText('DRIVING LAB  /  PLEOS', 512, 86);
  const bannerMesh = new THREE.Mesh(new THREE.PlaneGeometry(31, 1.8), new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(banner), side: THREE.DoubleSide }));
  bannerMesh.position.set(239.4, 11, 246); bannerMesh.rotation.y = -Math.PI / 2; scene.add(bannerMesh);
  const treeCount = 140, leaves = new THREE.InstancedMesh(new THREE.ConeGeometry(3.8, 11, 7), materials.leaf, treeCount), trunks = new THREE.InstancedMesh(new THREE.CylinderGeometry(.35, .5, 4, 5), materials.trunk, treeCount);
  for (let i = 0; i < treeCount; i++) {
    const angle = i * 2.39996, radius = 210 + (i % 7) * 23;
    const x = 240 + Math.cos(angle) * radius, z = 160 + Math.sin(angle) * radius * .7;
    dummy.rotation.set(0, angle, 0); dummy.position.set(x, 7 + i % 3, z); dummy.scale.setScalar(.8 + (i % 4) * .18); dummy.updateMatrix(); leaves.setMatrixAt(i, dummy.matrix);
    dummy.position.y = 2; dummy.scale.setScalar(1); dummy.updateMatrix(); trunks.setMatrixAt(i, dummy.matrix);
  }
  scene.add(leaves, trunks);
  for (let i = 0; i < 9; i++) {
    const hill = new THREE.Mesh(new THREE.ConeGeometry(100 + i * 5, 100 + i % 3 * 40, 5), mat(i % 2 ? 0x839c8c : 0x78918a));
    hill.position.set(-130 + i * 100, 15, -220 - i % 3 * 70); scene.add(hill);
  }
  const car = new THREE.Group(), body = new THREE.Group(); scene.add(car); car.add(body);
  box(0, .62, 0, 4.5, .6, 1.82, materials.blue, body);
  box(-.25, 1.13, 0, 2.15, .56, 1.57, materials.blue, body);
  box(.78, 1.15, 0, .06, .48, 1.48, mat(0x233b4a, { metalness: .2 }), body).rotation.z = -.32;
  box(-1.25, 1.12, 0, .06, .45, 1.45, materials.dark, body).rotation.z = .25;
  for (const side of [-1, 1]) {
    box(-.18, 1.17, side * .796, 1.8, .38, .025, materials.dark, body);
    box(2.26, .67, side * .62, .04, .14, .47, new THREE.MeshBasicMaterial({ color: 0xe3fbff }), body);
    box(-2.26, .73, side * .63, .04, .13, .5, new THREE.MeshBasicMaterial({ color: 0xe86252 }), body);
    box(.6, 1.12, side * 1.01, .23, .15, .22, materials.blue, body);
  }
  box(2.27, .43, 0, .05, .22, 1.15, materials.dark, body);
  box(-1.95, 1.0, 0, .36, .08, 1.85, materials.dark, body);
  const wheels = S.CORNERS.map(c => {
    const steer = new THREE.Group(), spin = new THREE.Group(); car.add(steer); steer.add(spin);
    const tyre = new THREE.Mesh(new THREE.CylinderGeometry(.33, .33, .25, 16), materials.dark); tyre.rotation.x = Math.PI / 2; spin.add(tyre);
    const rim = new THREE.Mesh(new THREE.CylinderGeometry(.23, .23, .26, 8), materials.metal); rim.rotation.x = Math.PI / 2; spin.add(rim);
    return { steer, spin, c };
  });
  const targetCamera = new THREE.Vector3(), look = new THREE.Vector3(); let first = true;
  const resize = new ResizeObserver(() => { const w = container.clientWidth, h = container.clientHeight; renderer.setSize(w, h); camera.aspect = w / h; camera.updateProjectionMatrix(); }); resize.observe(container);
  return {
    render(state, mode) {
      car.position.set(state.x, 0, state.y); car.rotation.y = -state.heading;
      body.position.y = state.heave; body.rotation.set(-state.roll, 0, state.pitch);
      wheels.forEach((w, i) => { const dynamic = state.wheels[i]; w.steer.position.set(w.c.x, .33 + dynamic.road, w.c.y); w.steer.rotation.y = w.c.x > 0 ? -state.roadAngle : 0; w.spin.rotation.z = -dynamic.rotation; });
      const forward = new THREE.Vector3(Math.cos(state.heading), 0, Math.sin(state.heading));
      if (mode === 'overview') { targetCamera.set(240, 340, 430); look.set(240, 0, 160); body.visible = true; }
      else if (mode === 'hood') { targetCamera.set(state.x + forward.x * 1.4, 1.45 + state.heave, state.y + forward.z * 1.4); look.copy(targetCamera).addScaledVector(forward, 35); look.y = .8; body.visible = false; }
      else { targetCamera.set(state.x - forward.x * 9, 4.8 + state.heave, state.y - forward.z * 9); look.set(state.x + forward.x * 8, 1, state.y + forward.z * 8); body.visible = true; }
      camera.position.lerp(targetCamera, first || mode !== 'chase' ? 1 : .13); first = false; camera.lookAt(look); renderer.render(scene, camera);
    },
    stats() { return { drawCalls: renderer.info.render.calls, triangles: renderer.info.render.triangles, webgl: renderer.capabilities.isWebGL2 }; }
  };
}
