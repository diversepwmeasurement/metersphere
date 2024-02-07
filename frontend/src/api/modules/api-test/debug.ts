import MSR from '@/api/http/index';
import {
  AddApiDebugUrl,
  AddDebugModuleUrl,
  DeleteDebugModuleUrl,
  ExecuteApiDebugUrl,
  GetDebugModuleCountUrl,
  GetDebugModulesUrl,
  MoveDebugModuleUrl,
  UpdateApiDebugUrl,
  UpdateDebugModuleUrl,
} from '@/api/requrls/api-test/debug';

import {
  AddDebugModuleParams,
  ExecuteRequestParams,
  SaveDebugParams,
  UpdateDebugModule,
  UpdateDebugParams,
} from '@/models/apiTest/debug';
import { ModuleTreeNode, MoveModules } from '@/models/common';

// 获取模块树
export function getDebugModules() {
  return MSR.get<ModuleTreeNode[]>({ url: GetDebugModulesUrl });
}

// 删除模块
export function deleteDebugModule(deleteId: string) {
  return MSR.get({ url: DeleteDebugModuleUrl, params: deleteId });
}

// 添加模块
export function addDebugModule(data: AddDebugModuleParams) {
  return MSR.post({ url: AddDebugModuleUrl, data });
}

// 移动模块
export function moveDebugModule(data: MoveModules) {
  return MSR.post({ url: MoveDebugModuleUrl, data });
}

// 更新模块
export function updateDebugModule(data: UpdateDebugModule) {
  return MSR.post({ url: UpdateDebugModuleUrl, data });
}

// 模块数量统计
export function getDebugModuleCount(data: { keyword: string }) {
  return MSR.post({ url: GetDebugModuleCountUrl, data });
}

// 执行调试
export function executeDebug(data: ExecuteRequestParams) {
  return MSR.post<ExecuteRequestParams>({ url: ExecuteApiDebugUrl, data });
}

// 新增调试
export function addDebug(data: SaveDebugParams) {
  return MSR.post({ url: AddApiDebugUrl, data });
}

// 更新调试
export function updateDebug(data: UpdateDebugParams) {
  return MSR.post({ url: UpdateApiDebugUrl, data });
}