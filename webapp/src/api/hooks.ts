import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

export function useRegions() {
  return useQuery({ queryKey: ['regions'], queryFn: () => api.regions.list() });
}

export function useRegion(code: string) {
  return useQuery({ queryKey: ['regions', code], queryFn: () => api.regions.get(code), enabled: !!code });
}

export function useDepartments(params?: Record<string, string>) {
  return useQuery({ queryKey: ['departments', params], queryFn: () => api.departments.list(params) });
}

export function useDepartment(code: string) {
  return useQuery({ queryKey: ['departments', code], queryFn: () => api.departments.get(code), enabled: !!code });
}

export function useCities(params?: Record<string, string>) {
  return useQuery({ queryKey: ['cities', params], queryFn: () => api.cities.list(params) });
}

export function useCity(code: string) {
  return useQuery({ queryKey: ['cities', code], queryFn: () => api.cities.get(code), enabled: !!code });
}

export function useTransactions(params?: Record<string, string>) {
  return useQuery({ queryKey: ['transactions', params], queryFn: () => api.transactions.list(params) });
}

export function useTransactionStats(params?: Record<string, string>) {
  return useQuery({ queryKey: ['transactionStats', params], queryFn: () => api.transactions.stats(params) });
}

export function useGeoRegions() {
  return useQuery({ queryKey: ['geo', 'regions'], queryFn: () => api.geo.regions() });
}

export function useGeoDepartments(regionCode?: string) {
  return useQuery({ queryKey: ['geo', 'departments', regionCode], queryFn: () => api.geo.departments(regionCode) });
}
