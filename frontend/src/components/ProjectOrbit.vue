<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import type { Project } from "../types";

const props = defineProps<{ projects: Project[] }>();
const emit = defineEmits<{ select: [id: string] }>();
const host = ref<HTMLDivElement | null>(null);
const hoverName = ref("");
let scene: THREE.Scene;
let camera: THREE.PerspectiveCamera;
let renderer: THREE.WebGLRenderer;
let controls: OrbitControls;
let mesh: THREE.InstancedMesh | undefined;
let rendered: Project[] = [];
let frame = 0;
let resizeObserver: ResizeObserver;
const pointer = new THREE.Vector2();
const raycaster = new THREE.Raycaster();

function hash(value: string) {
  let result = 0;
  for (let index = 0; index < value.length; index += 1) result = ((result << 5) - result + value.charCodeAt(index)) | 0;
  return Math.abs(result);
}

function dispose() {
  while (scene?.children.length) {
    const object = scene.children.pop()!;
    if (object instanceof THREE.Mesh || object instanceof THREE.LineSegments || object instanceof THREE.Points) {
      object.geometry.dispose();
      const materials = Array.isArray(object.material) ? object.material : [object.material];
      materials.forEach(material => material.dispose());
    }
  }
}

function rebuild() {
  if (!scene) return;
  dispose();
  rendered = props.projects.slice(0, 24);
  const positions: THREE.Vector3[] = [];
  rendered.forEach((project, index) => {
    const seed = hash(project.id);
    const angle = index * 2.399 + (seed % 37) / 50;
    const radius = 2.2 + (index % 4) * 0.85;
    positions.push(new THREE.Vector3(Math.cos(angle) * radius, ((seed % 300) - 150) / 120, Math.sin(angle) * radius));
  });
  if (rendered.length) {
    mesh = new THREE.InstancedMesh(new THREE.IcosahedronGeometry(0.18, 1), new THREE.MeshBasicMaterial({ color: 0x52d6ff }), rendered.length);
    const matrix = new THREE.Matrix4();
    positions.forEach((position, index) => {
      const scale = 0.8 + Math.min(rendered[index].fileCount / 500, 1.5);
      matrix.makeScale(scale, scale, scale);
      matrix.setPosition(position);
      mesh!.setMatrixAt(index, matrix);
    });
    mesh.instanceMatrix.needsUpdate = true;
    scene.add(mesh);
    const edges: number[] = [];
    positions.forEach((position, index) => {
      if (!index) return;
      const previous = positions[(index - 1) % positions.length];
      edges.push(...position.toArray(), ...previous.toArray());
      if (index > 2) edges.push(...position.toArray(), ...positions[Math.floor(index / 2)].toArray());
    });
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.Float32BufferAttribute(edges, 3));
    scene.add(new THREE.LineSegments(geometry, new THREE.LineBasicMaterial({ color: 0x35596f, transparent: true, opacity: 0.45 })));
  }
  const dust = new Float32Array(420 * 3);
  for (let index = 0; index < 420; index += 1) {
    const seed = hash(`atlas-${index}`);
    dust[index * 3] = ((seed % 1800) - 900) / 110;
    dust[index * 3 + 1] = (((seed >> 3) % 900) - 450) / 130;
    dust[index * 3 + 2] = (((seed >> 7) % 1800) - 900) / 110;
  }
  const dustGeometry = new THREE.BufferGeometry();
  dustGeometry.setAttribute("position", new THREE.BufferAttribute(dust, 3));
  scene.add(new THREE.Points(dustGeometry, new THREE.PointsMaterial({ color: 0x5e7f93, size: 0.025, transparent: true, opacity: 0.45 })));
}

function hit(event: PointerEvent) {
  if (!host.value || !mesh) return undefined;
  const rect = host.value.getBoundingClientRect();
  pointer.set(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1);
  raycaster.setFromCamera(pointer, camera);
  return raycaster.intersectObject(mesh)[0];
}

function onMove(event: PointerEvent) {
  const result = hit(event);
  hoverName.value = result?.instanceId !== undefined ? rendered[result.instanceId]?.name || "" : "";
  renderer.domElement.style.cursor = hoverName.value ? "pointer" : "grab";
}

function onClick(event: PointerEvent) {
  const result = hit(event);
  if (result?.instanceId !== undefined) emit("select", rendered[result.instanceId].id);
}

function resize() {
  if (!host.value) return;
  const rect = host.value.getBoundingClientRect();
  renderer.setSize(rect.width, rect.height, false);
  camera.aspect = rect.width / Math.max(rect.height, 1);
  camera.updateProjectionMatrix();
}

onMounted(() => {
  if (!host.value) return;
  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
  camera.position.set(0, 5.5, 12);
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
  host.value.appendChild(renderer.domElement);
  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.enablePan = false;
  controls.minDistance = 7;
  controls.maxDistance = 18;
  renderer.domElement.addEventListener("pointermove", onMove);
  renderer.domElement.addEventListener("click", onClick);
  resizeObserver = new ResizeObserver(resize);
  resizeObserver.observe(host.value);
  rebuild();
  resize();
  const animate = () => {
    frame = requestAnimationFrame(animate);
    controls.update();
    renderer.render(scene, camera);
  };
  animate();
});

watch(() => props.projects, rebuild, { deep: true });
onBeforeUnmount(() => {
  cancelAnimationFrame(frame);
  resizeObserver?.disconnect();
  controls?.dispose();
  renderer?.dispose();
  renderer?.domElement.removeEventListener("pointermove", onMove);
  renderer?.domElement.removeEventListener("click", onClick);
  dispose();
});
</script>

<template>
  <div ref="host" class="project-orbit" aria-label="真实仓库关系概览">
    <span v-if="hoverName" class="orbit-tooltip">{{ hoverName }}</span>
    <div v-if="!projects.length" class="orbit-empty">导入仓库后，这里会显示真实项目节点</div>
  </div>
</template>
