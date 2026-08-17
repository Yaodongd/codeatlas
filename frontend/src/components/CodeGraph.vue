<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import type { SourceFile } from "../types";

const props = defineProps<{ files: SourceFile[]; activePath?: string | null }>();
const emit = defineEmits<{ select: [path: string] }>();

const host = ref<HTMLDivElement | null>(null);
const hoverName = ref("");
const hoverX = ref(0);
const hoverY = ref(0);
let renderer: THREE.WebGLRenderer | undefined;
let scene: THREE.Scene | undefined;
let camera: THREE.PerspectiveCamera | undefined;
let controls: OrbitControls | undefined;
let nodes: THREE.InstancedMesh | undefined;
let frame = 0;
let resizeObserver: ResizeObserver | undefined;
let renderedFiles: SourceFile[] = [];
const raycaster = new THREE.Raycaster();
const pointer = new THREE.Vector2();

function hash(value: string) {
  let result = 2166136261;
  for (let i = 0; i < value.length; i += 1) result = Math.imul(result ^ value.charCodeAt(i), 16777619);
  return Math.abs(result);
}

function folderOf(path: string) {
  const parts = path.split("/");
  return parts.length > 2 ? parts[parts.length - 2] : parts[0];
}

function disposeScene() {
  if (!scene) return;
  scene.traverse(object => {
    if (object instanceof THREE.Mesh || object instanceof THREE.LineSegments || object instanceof THREE.Points) {
      object.geometry?.dispose();
      const materials = Array.isArray(object.material) ? object.material : [object.material];
      materials.forEach(material => material?.dispose());
    }
  });
  while (scene.children.length) scene.remove(scene.children[0]);
}

function rebuild() {
  if (!scene) return;
  disposeScene();
  renderedFiles = props.files.slice(0, 180);
  if (!renderedFiles.length) return;

  const folders = [...new Set(renderedFiles.map(file => folderOf(file.path)))];
  const centers = new Map<string, THREE.Vector3>();
  folders.forEach((folder, index) => {
    const angle = (index / Math.max(folders.length, 1)) * Math.PI * 2;
    const radius = 4.4 + (index % 3) * 1.3;
    centers.set(folder, new THREE.Vector3(Math.cos(angle) * radius, ((index % 4) - 1.5) * 1.25, Math.sin(angle) * radius));
  });

  const positions = renderedFiles.map(file => {
    const base = centers.get(folderOf(file.path)) || new THREE.Vector3();
    const seed = hash(file.path);
    const angle = (seed % 628) / 100;
    const radius = 0.45 + ((seed >> 4) % 160) / 100;
    return base.clone().add(new THREE.Vector3(Math.cos(angle) * radius, (((seed >> 8) % 180) - 90) / 100, Math.sin(angle) * radius));
  });

  const geometry = new THREE.SphereGeometry(0.105, 14, 14);
  const material = new THREE.MeshBasicMaterial({ color: 0x67d7ff, transparent: true, opacity: 0.96 });
  nodes = new THREE.InstancedMesh(geometry, material, renderedFiles.length);
  const matrix = new THREE.Matrix4();
  positions.forEach((position, index) => {
    matrix.setPosition(position);
    nodes!.setMatrixAt(index, matrix);
  });
  nodes.instanceMatrix.needsUpdate = true;
  scene.add(nodes);

  const edges: number[] = [];
  renderedFiles.forEach((file, index) => {
    const folder = folderOf(file.path);
    const candidates = renderedFiles
      .map((other, otherIndex) => ({ other, otherIndex }))
      .filter(item => item.otherIndex !== index && folderOf(item.other.path) === folder)
      .slice(0, 2);
    candidates.forEach(({ otherIndex }) => edges.push(...positions[index].toArray(), ...positions[otherIndex].toArray()));
    if (index > 0 && index % 7 === 0) edges.push(...positions[index].toArray(), ...positions[index - 1].toArray());
  });
  const edgeGeometry = new THREE.BufferGeometry();
  edgeGeometry.setAttribute("position", new THREE.Float32BufferAttribute(edges, 3));
  scene.add(new THREE.LineSegments(edgeGeometry, new THREE.LineBasicMaterial({ color: 0x3f7295, transparent: true, opacity: 0.23 })));

  const dustCount = 480;
  const dust = new Float32Array(dustCount * 3);
  for (let i = 0; i < dustCount; i += 1) {
    const seed = hash(`dust-${i}`);
    dust[i * 3] = ((seed % 2000) - 1000) / 70;
    dust[i * 3 + 1] = (((seed >> 5) % 1200) - 600) / 90;
    dust[i * 3 + 2] = (((seed >> 10) % 2000) - 1000) / 70;
  }
  const dustGeometry = new THREE.BufferGeometry();
  dustGeometry.setAttribute("position", new THREE.BufferAttribute(dust, 3));
  scene.add(new THREE.Points(dustGeometry, new THREE.PointsMaterial({ color: 0x52d6ff, size: 0.025, transparent: true, opacity: 0.28 })));
  updateSelection();
}

function updateSelection() {
  if (!nodes || !scene) return;
  const previous = scene.getObjectByName("active-node");
  if (previous) scene.remove(previous);
  const matrix = new THREE.Matrix4();
  let selectedPosition: THREE.Vector3 | undefined;
  renderedFiles.forEach((file, index) => {
    nodes!.getMatrixAt(index, matrix);
    const position = new THREE.Vector3().setFromMatrixPosition(matrix);
    if (file.path === props.activePath) selectedPosition = position.clone();
    matrix.makeScale(file.path === props.activePath ? 2.8 : 1, file.path === props.activePath ? 2.8 : 1, file.path === props.activePath ? 2.8 : 1);
    matrix.setPosition(position);
    nodes!.setMatrixAt(index, matrix);
  });
  nodes.instanceMatrix.needsUpdate = true;
  if (selectedPosition) {
    const halo = new THREE.Mesh(
      new THREE.SphereGeometry(0.31, 20, 20),
      new THREE.MeshBasicMaterial({ color: 0xb9ff4b, transparent: true, opacity: 0.88 })
    );
    halo.name = "active-node";
    halo.position.copy(selectedPosition);
    scene.add(halo);
  }
}

function setPointer(event: PointerEvent) {
  if (!host.value || !camera || !nodes) return undefined;
  const rect = host.value.getBoundingClientRect();
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
  raycaster.setFromCamera(pointer, camera);
  return raycaster.intersectObject(nodes)[0];
}

function onMove(event: PointerEvent) {
  const hit = setPointer(event);
  hoverName.value = hit?.instanceId !== undefined ? renderedFiles[hit.instanceId]?.path || "" : "";
  hoverX.value = event.offsetX + 14;
  hoverY.value = event.offsetY + 14;
  if (renderer?.domElement) renderer.domElement.style.cursor = hoverName.value ? "pointer" : "grab";
}

function onClick(event: PointerEvent) {
  const hit = setPointer(event);
  if (hit?.instanceId !== undefined) emit("select", renderedFiles[hit.instanceId].path);
}

function resize() {
  if (!host.value || !renderer || !camera) return;
  const { width, height } = host.value.getBoundingClientRect();
  renderer.setSize(width, height, false);
  camera.aspect = width / Math.max(height, 1);
  camera.updateProjectionMatrix();
}

onMounted(() => {
  if (!host.value) return;
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.6));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  host.value.appendChild(renderer.domElement);
  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
  camera.position.set(0, 7, 17);
  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.055;
  controls.enablePan = true;
  controls.minDistance = 7;
  controls.maxDistance = 28;
  renderer.domElement.addEventListener("pointermove", onMove);
  renderer.domElement.addEventListener("click", onClick);
  resizeObserver = new ResizeObserver(resize);
  resizeObserver.observe(host.value);
  rebuild();
  resize();
  const animate = () => {
    frame = requestAnimationFrame(animate);
    controls?.update();
    if (scene && renderer && camera) renderer.render(scene, camera);
  };
  animate();
});

watch(() => props.files, rebuild, { deep: true });
watch(() => props.activePath, updateSelection);

onBeforeUnmount(() => {
  cancelAnimationFrame(frame);
  resizeObserver?.disconnect();
  if (renderer) {
    renderer.domElement.removeEventListener("pointermove", onMove);
    renderer.domElement.removeEventListener("click", onClick);
    renderer.dispose();
    renderer.domElement.remove();
  }
  controls?.dispose();
  disposeScene();
});
</script>

<template>
  <div ref="host" class="graph-canvas" aria-label="可交互代码关系图">
    <div v-if="hoverName" class="graph-tooltip" :style="{ left: `${hoverX}px`, top: `${hoverY}px` }">{{ hoverName }}</div>
  </div>
</template>
