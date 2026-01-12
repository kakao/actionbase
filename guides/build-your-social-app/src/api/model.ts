interface Edge {
  version: number,
  source: any,
  target: any,
  properties: Record<string, any | null>
}

interface DatabaseEntity {
  active: boolean,
  name: string,
  desc: string
}

interface TableEntity {
  active: boolean,
  name: string,
  desc: string,
}

interface DataPayload {
  edges: Edge[],
  count: number,
  offset: string,
  hasNext: boolean
}

interface DataCountPayload {
  counts: EdgeCount[],
  count: number
}

interface EdgeCount {
  start: any,
  direction: string,
  count: number
}

interface EdgeMutation {
  mutations: MutationItem[]
}

interface MutationItem {
  type: string,
  edge: Edge
}

interface EdgeMutationResponse {
  results: Item[]
}

interface Item {
  source: any,
  target: any,
  status: string,
  count: number
}

interface CommandRequest {
  command: string
}

interface CommandResponse {
  success: boolean,
  result: string | undefined,
  error: string | undefined
}
