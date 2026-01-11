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
    stepIndex: 2,
    command: `load ./hands-on-social/data/build-your-social-app-preset.txt`,
  },
  {
    stepIndex: 3,
    command: 'use database social',
    context: {
      database: 'social'
    }
  },
  {
    stepIndex: 6,
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
    command: 'get user_follows --source doki --target merlin',
  },
  {
    stepIndex: 10,
    command: 'count user_follows --start merlin --direction IN',
  },
  {
    stepIndex: 12,
    command: 'scan user_follows --start merlin --index created_at_desc --direction IN',
  },
  {
    stepIndex: 15,
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
    command: 'get user_likes --source doki --target 1',
  }
];

export default stepCommands;

