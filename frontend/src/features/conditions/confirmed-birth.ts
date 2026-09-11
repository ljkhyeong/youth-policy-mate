"use client";

// 화면 이동 중에만 사용한다. 저장소·URL에는 기록하지 않는다.
let birthDate: string | null = null;
export function rememberConfirmedBirth(value: string) { birthDate = value; }
export function readConfirmedBirth() { return birthDate; }
export function clearConfirmedBirth() { birthDate = null; }
