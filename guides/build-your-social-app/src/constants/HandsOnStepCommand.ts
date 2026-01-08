export interface HandsOnStepCommand {
  stepIndex: number;
  database?: string;
  command?: string;
}

export const steps: HandsOnStepCommand[] = [
  {
    stepIndex: 2,
    command: `load ./hands-on-social/data/build-your-social-app-preset.txt`,
  },
  {
    stepIndex: 3,
    command: 'use database social',
  },
  {
    stepIndex: 6,
    database: 'social',
    command: `create table \\
--database social \\
--storage datastore://guides/user_follows \\
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
  {
    stepIndex: 7,
    database: 'social',
    command: `mutate user_follows \\
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
  {
    stepIndex: 9,
    database: 'social',
    command: 'get user_follows --source doki --target merlin',
  },
  {
    stepIndex: 10,
    database: 'social',
    command: 'count user_follows --start merlin --direction IN',
  },
  {
    stepIndex: 12,
    database: 'social',
    command: 'scan user_follows --start merlin --index created_at_desc --direction IN',
  },
  {
    stepIndex: 15,
    database: 'social',
    command: `mutate user_likes \\
--type INSERT \\
--table user_likes \\
--source doki \\
--target 1 \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'`,
  },
  {
    stepIndex: 17,
    database: 'social',
    command: 'get user_likes --source doki --target 1',
  }
];

export default steps;

