export interface Edge {
  version: number,
  source: any,
  target: any,
  properties: Record<string, any | null>
}

export interface DatabaseEntity {
  active: boolean,
  name: string,
  desc: string
}

export interface TableEntity {
  active: boolean,
  name: string,
  desc: string,
}

export interface DataPayload {
  edges: Edge[],
  count: number,
  offset: string,
  hasNext: boolean
}

export interface DataCountPayload {
  counts: EdgeCount[],
  count: number
}

export interface EdgeCount {
  start: any,
  direction: string,
  count: number
}

export interface EdgeMutation {
  mutations: MutationItem[]
}

export interface MutationItem {
  type: string,
  edge: Edge
}

export interface EdgeMutationResponse {
  results: MutationResult[]
}

export interface MutationResult {
  source: any,
  target: any,
  status: string,
  count: number
}

export interface CommandRequest {
  command: string
}

export interface CommandResponse {
  success: boolean,
  result: string | undefined,
  error: string | undefined
}
