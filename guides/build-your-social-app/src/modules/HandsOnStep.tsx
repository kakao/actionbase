export interface HandsOnStep {
  title: string;
  database?: string;
  table?: string;
  finalDatabase?: string;
  finalTable?: string;
  commands?: Command[];
}

export interface Command {
  text: string;
  database?: string;
  table?: string;
  result?: string;
}

export const steps: HandsOnStep[] = [
  {
    title: '/** Welcome to Actionbase! **/',
    commands: [],
  },
  {
    title: '/** Create Database and Storage **/',
    commands: [
      {text: `<span class="terminal-reserved-word">create database --name</span> social <span class="terminal-reserved-word">--comment</span> 'social database'`},
      {text: '<span class="terminal-reserved-word">use database</span> social'},
      {
        database: "social",
        text: `<span class="terminal-reserved-word">create</span> storage \\
<span class="terminal-reserved-word">--hbaseNamespace</span> test \\
<span class="terminal-reserved-word">--hbaseTable</span> table1 \\
<span class="terminal-reserved-word">--storageType</span> HBASE \\
<span class="terminal-reserved-word">--name</span> default \\
<span class="terminal-reserved-word">--comment</span> 'default storage'`
      }
    ],
    finalDatabase: 'social'
  },
  {
    title: '/** Load user_posts and user_likes data **/',
    database: 'social',
    commands: [
      {
        text: `<span class="terminal-reserved-word">load</span> ../data.txt`,
        result: `Storage user_posts_storage is created \\
Table user_posts is created \\
5 edges are mutated (total: 5, failed: 0) \\
Storage user_likes_storage is created \\
Table user_likes is created \\
4 edges are mutated (total: 4, failed: 0) \\
Took  0.2843  seconds`
      },
    ],
  },
  {
    title: '/** Navigate to Search **/',
    database: 'social',
    commands: [],
  },
  {
    title: '/** Let\'s follow merlin and emeth **/',
    database: 'social',
    commands: [],
  },
  {
    title: '/** Create Table user_follows **/',
    database: 'social',
    commands: [
      {
        text: `<span class="terminal-reserved-word">create</span> table \\
<span class="terminal-reserved-word">--database</span> social \\
<span class="terminal-reserved-word">--storage</span> default \\
<span class="terminal-reserved-word">--name</span> user_follows \\
<span class="terminal-reserved-word">--comment</span> 'user follows table' \\
<span class="terminal-reserved-word">--type</span> INDEXED \\
<span class="terminal-reserved-word">--direction</span> BOTH \\
<span class="terminal-reserved-word">--schema</span> '{
  "src": {
    "type": "STRING",
    "desc": "userId"
  },
    "tgt": {
      "type": "STRING",
      "desc": "followee Id"
    },
    "fields": [
      {
        "name": "createdAt",
        "type": "LONG",
        "desc": "created at",
        "nullable": false
      }
    ]
  }' \\
<span class="terminal-reserved-word">--indices</span> '[
  {
    "name": "created_at_desc",
    "fields": [
      {
        "name": "createdAt",
        "order": "DESC"
      }
    ],
      "desc": "order by createdAt"
    }
  ]'
`,
      },
      {
        text: '<span class="terminal-reserved-word">use table</span> user_follows',
      },
    ],
    finalTable: 'user_follows'
  },
  {
    title: '/** Insert Edge: me (doki) -> merlin **/ ',
    database: 'social',
    table: 'user_follows',
    commands: [
      {
        text: `<span class="terminal-reserved-word">mutate</span> \\
<span class="terminal-reserved-word">--type</span> INSERT \\
<span class="terminal-reserved-word">--table</span> user_follows \\
<span class="terminal-reserved-word">--source</span> doki \\
<span class="terminal-reserved-word">--target</span> merlin \\
<span class="terminal-reserved-word">--version</span> __CURRENT_TIMESTAMP__ \\
<span class="terminal-reserved-word">--properties</span> '{
    "createdAt": __CURRENT_TIMESTAMP__
}'
`,
      },
    ],
  },
  {
    title: '/** Show data **/',
    database: 'social',
    table: 'user_follows',
    commands: [
      {
        text: '<span class="terminal-reserved-word">get --source</span> doki <span class="terminal-reserved-word">--target</span> merlin',
      },
    ],
  },
  {
    title: '/** Follow emeth **/',
    database: 'social',
    table: 'user_follows',
    commands: [],
  },
  {
    title: '/** Insert Edge: me (doki) -> emeth **/',
    database: 'social',
    table: 'user_follows',
    commands: [
      {
        text: `<span class="terminal-reserved-word">mutate</span> \\
<span class="terminal-reserved-word">--type</span> INSERT \\
<span class="terminal-reserved-word">--table</span> user_follows \\
<span class="terminal-reserved-word">--source</span> doki \\
<span class="terminal-reserved-word">--target</span> emeth \\
<span class="terminal-reserved-word">--version</span> __CURRENT_TIMESTAMP__ \\
<span class="terminal-reserved-word">--properties</span> '{
    "createdAt": __CURRENT_TIMESTAMP__
}'
`,
      },
    ],
  },
  {
    title: '/** Show data **/',
    database: 'social',
    table: 'user_follows',
    commands: [
      {
        text: '<span class="terminal-reserved-word">get --source</span> doki <span class="terminal-reserved-word">--target</span> emeth',
      },
    ],
  },
  {
    title: '/** Navigate to Profile **/',
    database: 'social',
    table: 'user_follows',
    commands: [],
  },
  {
    title: '/** Navigate to Post **/',
    database: 'social',
    table: 'user_follows',
    commands: [],
  },
  {
    title: '/** Insert Edge: me (doki) -> Post **/',
    database: 'social',
    table: 'user_follows',
    commands: [
      {
        text: '<span class="terminal-reserved-word">use table</span> user_likes',
      },
      {
        table: "user_likes",
        text: `<span class="terminal-reserved-word">mutate</span> \\
<span class="terminal-reserved-word">--type</span> INSERT \\
<span class="terminal-reserved-word">--table</span> user_likes \\
<span class="terminal-reserved-word">--source</span> doki \\
<span class="terminal-reserved-word">--target</span> 1 \\
<span class="terminal-reserved-word">--version</span> __CURRENT_TIMESTAMP__ \\
<span class="terminal-reserved-word">--properties</span> '{
    "createdAt": __CURRENT_TIMESTAMP__
}'`
      },
    ],
  },
  {
    title: '/** Show data **/',
    database: 'social',
    table: 'user_likes',
    commands: [
      {
        text: '<span class="terminal-reserved-word">get --source</span> doki <span class="terminal-reserved-word">--target</span> 1',
      },
    ]
  },
  {
    title: '/** Navigate to Profile **/',
    database: 'social',
    table: 'user_likes',
    commands: [],
  },
  {
    title: '/** Get Counts **/',
    database: 'social',
    table: 'user_likes',
    commands: [
      {
        text: '<span class="terminal-reserved-word">use table</span> user_posts'
      },
      {
        text: '<span class="terminal-reserved-word">count --start</span> doki <span class="terminal-reserved-word">--direction</span> OUT',
        table: "user_posts"
      },
      {
        text: '<span class="terminal-reserved-word">use table</span> user_follows',
        table: "user_posts"
      },
      {
        text: '<span class="terminal-reserved-word">count --start</span> doki <span class="terminal-reserved-word">--direction</span> IN',
        table: "user_follows"
      },
      {
        text: '<span class="terminal-reserved-word">count --start</span> doki <span class="terminal-reserved-word">--direction</span> OUT',
        table: "user_follows"
      },
    ],
  },
  {
    title: '/** Navigate to Followers **/',
    database: 'social',
    table: 'user_follows',
    commands: [],
  },
  {
    title: '/** Scan Edges **/',
    database: 'social',
    table: 'user_follows',
    commands: [
      {
        text: '<span class="terminal-reserved-word">scan --start</span> doki <span class="terminal-reserved-word">--index</span> created_at_desc <span class="terminal-reserved-word">--direction</span> OUT',
      },
    ],
  },
  {
    title: '/** Navigate to Feed **/',
    database: 'social',
    table: 'user_follows',
    commands: [],
  },
  {
    title: '/** Scan Edges **/',
    database: 'social',
    table: 'user_follows',
    commands: [
      {
        text: '<span class="terminal-reserved-word">scan --start</span> doki <span class="terminal-reserved-word">--index</span> created_at_desc <span class="terminal-reserved-word">--direction</span> OUT',
        table: "user_follows"
      },
      {
        text: '<span class="terminal-reserved-word">use table</span> user_posts',
        table: "user_follows"
      },
      {
        text: '<span class="terminal-reserved-word">scan --start</span> merlin <span class="terminal-reserved-word">--index</span> created_at_desc <span class="terminal-reserved-word">--direction</span> OUT',
        table: "user_posts"
      },
      {
        text: '<span class="terminal-reserved-word">scan --start</span> emeth <span class="terminal-reserved-word">--index</span> created_at_desc <span class="terminal-reserved-word">--direction</span> OUT',
        table: "user_posts"
      },
    ],
  },
  {
    title: '/** GoodBye! **/',
    commands: [],
  },
];

export default steps;
