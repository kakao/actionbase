export interface HandsOnStep {
  database?: string;
  finalDatabase?: string;
  commands?: Command[];
}

export interface Command {
  text: string;
  database?: string;
}

export const steps: HandsOnStep[] = [
  {
    commands: [],
  },
  {
    commands: [
      {text: `create database --name social --comment 'social database'`},
      {text: 'use database social'},
      {
        database: "social",
        text: `create storage \\
--hbaseNamespace test \\
--hbaseTable table1 \\
--storageType HBASE \\
--name default \\
--comment 'default storage'`
      }
    ],
    finalDatabase: 'social'
  },
  {
    database: 'social',
    commands: [
      {
        text: `load ./hands-on-social/data/dump.txt`,
      },
    ],
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [
      {
        text: `create table \\
--database social \\
--storage default \\
--name user_follows \\
--comment 'user follows table' \\
--type INDEXED \\
--direction BOTH \\
--schema '{
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
--indices '[
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
    ],
  },
  {
    database: 'social',
    commands: [
      {
        text: `mutate user_follows\\
--type INSERT \\
--table user_follows \\
--source doki \\
--target merlin \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'
`,
      },
    ],
  },
  {
    database: 'social',
    commands: [
      {
        text: 'get user_follows --source doki --target merlin',
      },
    ],
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [
      {
        text: `mutate user_follows\\
--type INSERT \\
--table user_follows \\
--source doki \\
--target emeth \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'
`,
      },
    ],
  },
  {
    database: 'social',
    commands: [
      {
        text: 'get user_follows --source doki --target emeth',
      },
    ],
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [
      {
        text: `mutate user_likes\\
--type INSERT \\
--table user_likes \\
--source doki \\
--target 1 \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'`
      },
    ],
  },
  {
    database: 'social',
    commands: [
      {
        text: 'get user_likes --source doki --target 1',
      },
    ]
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [
      {
        text: 'count user_posts --start doki --direction OUT',
      },
      {
        text: 'count user_follows --start doki --direction IN',
      },
      {
        text: 'count user_follows --start doki --direction OUT',
      },
    ],
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [
      {
        text: 'scan user_follows --start doki --index created_at_desc --direction OUT',
      },
    ],
  },
  {
    database: 'social',
    commands: [],
  },
  {
    database: 'social',
    commands: [
      {
        text: 'scan user_follows --start doki --index created_at_desc --direction OUT',
      },
      {
        text: 'scan user_posts --start merlin --index created_at_desc --direction OUT',
      },
      {
        text: 'scan user_posts --start emeth --index created_at_desc --direction OUT',
      },
    ],
  },
  {
    commands: [],
  },
];

export default steps;

