export interface HandsOnStepCommand {
  stepIndex: number;
  command?: string;
  context?: CliContext;
}

export interface CliContext {
  database?: string;
}

export const stepCommands: HandsOnStepCommand[] = [
  {
    stepIndex: 3,
    command: `load preset build-your-social-app`,
  },
  {
    stepIndex: 4,
    command: 'use database social',
    context: {
      database: 'social'
    }
  },
  {
    stepIndex: 7,
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
    stepIndex: 8,
    command: `mutate user_follows \\
--type INSERT \\
--table user_follows \\
--source zipdoki \\
--target j4rami \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'
`,
  },
  {
    stepIndex: 9,
    command: 'get user_follows --source zipdoki --target j4rami',
  },
  {
    stepIndex: 10,
    command: 'count user_follows --start j4rami --direction IN',
  },
  {
    stepIndex: 11,
    command: 'scan user_follows --start j4rami --index created_at_desc --direction IN',
  },
  {
    stepIndex: 13,
    command: `mutate user_likes \\
--type INSERT \\
--table user_likes \\
--source zipdoki \\
--target 1 \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'`,
  },
  {
    stepIndex: 14,
    command: 'get user_likes --source zipdoki --target 1',
  }
];

export default stepCommands;

