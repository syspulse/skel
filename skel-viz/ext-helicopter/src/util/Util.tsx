import React, { useEffect, useState } from 'react';

export function truncateStr(data: string, limit:number) {
  if (data.length <= limit) return data;
  return `${data.slice(0, limit / 2)}...${data.slice(-limit / 2)}`;
};

export function truncateAddr(addr: string,limit:number = 20) {
  if (addr.length <= limit) return addr;
  let a = `${addr.slice(0, limit/2)}...${addr.slice(-limit/2)}`;
  return a.toLocaleLowerCase();
};

export function truncateAddrChart(addr: string,limit:number = 10) {
  if (addr.length <= limit) return addr;
  let a = `${addr.slice(0, limit/2)}...${addr.slice(-limit/2)}`;
  return a.toLocaleLowerCase();
};