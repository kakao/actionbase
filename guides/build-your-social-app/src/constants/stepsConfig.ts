import {get, getTable} from "../api/actionbase";
import {DATABASE, TABLE} from "./index";
import {me} from "./dummy";

export interface NavigationConfig {
  to?: string;
  waitFor?: string[];
}

export interface CommandConfig {
  content: string;
  context?: {
    database?: string;
  };
}

export interface PopoverConfig {
  side?: 'top' | 'bottom' | 'left' | 'right' | 'over';
  align?: 'start' | 'center' | 'end';
  nextBtnText?: string;
  showButtons?: ('next' | 'previous' | 'close')[];
}

export interface StepConfig {
  index: number;
  titleNumber?: string;
  title?: string;
  description: string;
  element?: string;
  command?: CommandConfig;
  navigation?: {
    next?: NavigationConfig;
    prev?: NavigationConfig;
  };
  verifier?: () => Promise<boolean>;
  popover?: PopoverConfig;
}

export interface BreadCrumbStep {
  stepIndex: number;
  title?: string;
  isActive?: boolean;
  isCompleted?: boolean;
  subSteps?: BreadCrumbStep[];
}

export const STEP = {
  NEXT: 'next',
  PREV: 'prev',
  CLOSE: 'close',
  RELOAD: 'reload'
} as const;

const verifyPresetLoaded = async () => {
  const [userPosts, userLikes] = await Promise.all([
    getTable(DATABASE.SOCIAL, TABLE.USER_POSTS, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_LIKES, false),
  ]);
  return !(!userPosts || userPosts?.active === false || !userLikes || userLikes?.active === false);
};

const verifyFollowsTableCreated = async () => {
  const [userPosts, userLikes, userFollows] = await Promise.all([
    getTable(DATABASE.SOCIAL, TABLE.USER_POSTS, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_LIKES, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, false)
  ]);
  return !(
    !userPosts || userPosts?.active === false ||
    !userLikes || userLikes?.active === false ||
    !userFollows || userFollows?.active === false
  );
};

const verifyFollowCreated = async () => {
  const edgeState = await get(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, me.id, 'j4rami', false);
  return !(!edgeState || edgeState?.count < 1);
};

const verifyLikeCreated = async () => {
  const edgeState = await get(DATABASE.SOCIAL, TABLE.USER_LIKES, me.id, 1, false);
  return !(!edgeState || edgeState?.count < 1);
};

const verifyAllTablesExist = async () => {
  const [userFollows, userPosts, userLikes] = await Promise.all([
    getTable(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_POSTS, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_LIKES, false),
  ]);
  return !(
    !userFollows || userFollows?.active === false ||
    !userPosts || userPosts?.active === false ||
    !userLikes || userLikes?.active === false
  );
};

export const stepsConfig: StepConfig[] = [
  // Step 0
  {
    index: 0,
    titleNumber: '1',
    title: "Welcome",
    description: `Welcome to the Actionbase hands-on guide.

In this guide, you'll work with a small but realistic social media dataset and build interaction features step by step.
Each step introduces a common social pattern and shows how Actionbase supports it through simple data operations.`,
    popover: {side: 'over', align: 'center', nextBtnText: 'start', showButtons: ['next', 'close']},
  },
  // Step 1
  {
    index: 1,
    description: `<img class="profile-image" src="${me.avatar}" /><p class="profile-name">zipdoki</p>Let's assume you are zipdoki.`,
    popover: {side: 'over', align: 'center'},
  },
  // Step 2
  {
    index: 2,
    titleNumber: '2',
    title: "Prepare the Environment",
    description: `Before we begin, let's prepare the environment for this guide.

To help you focus on interaction patterns rather than setup details, we provide a preset dataset and a ready-to-use database context.`,
    navigation: {
      next: {waitFor: ["[id='run-command-btn']"]},
    },
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 3
  {
    index: 3,
    title: "Load preset data",
    description: `Load the prepared data for this hands-on.

This step creates a database and tables with sample data commonly used in social media applications.`,
    element: "[id='run-command-btn']",
    command: {content: `load preset build-your-social-app`},
    navigation: {
      next: {waitFor: ["[id='run-command-btn']"]},
      prev: {},
    },
    verifier: verifyPresetLoaded,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 4
  {
    index: 4,
    title: "Set Database Context",
    description: `Set the current database context to <pre>\`social\`</pre>.
All subsequent steps in this guide assume this context.`,
    element: "[id='run-command-btn']",
    command: {content: 'use database social', context: {database: 'social'}},
    navigation: {
      next: {to: '/search', waitFor: ["[id='search-results-list']"]},
      prev: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyPresetLoaded,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 5
  {
    index: 5,
    titleNumber: '3',
    title: "Review the Prepared Data",
    description: `Before adding new interactions, take a moment to review the prepared data.

The dataset includes users and posts represented as nodes, along with existing interactions such as likes.
Rather than focusing on schema definitions, this guide emphasizes how Actionbase builds queryable relationships directly from interaction data.`,
    element: "[id='search-results-list']",
    navigation: {
      prev: {to: '/search', waitFor: ["[id='cli-commands']", "[id='run-command-btn']"]},
    },
    verifier: verifyPresetLoaded,
    popover: {side: 'right', align: 'start'},
  },
  // Step 6
  {
    index: 6,
    titleNumber: '4',
    title: "Follows",
    description: `In this step, you'll walk through an interactive flow to create and query follow relationships between users.

Follow relationships are a core feature in most social applications and serve as a good introduction to Actionbase's interaction model.`,
    navigation: {
      next: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyPresetLoaded,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 7
  {
    index: 7,
    title: "Create the \`user_follows\` Table",
    description: `Create a <pre>\`user_follows\`</pre> table to store follow interaction between users.

Each edge represents a single interaction: one user follows another.`,
    element: "[id='run-command-btn']",
    command: {
      content: `create table \\
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
        } ' \\
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
`
    },
    navigation: {
      next: {to: '/profile/j4rami', waitFor: ["[id='btn-profile-following']", "[id='run-command-btn']"]},
      prev: {},
    },
    verifier: verifyFollowsTableCreated,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 8
  {
    index: 8,
    title: "Make zipdoki Follow j4rami",
    description: `Write an interaction where zipdoki follows j4rami.

This single mutation adds an edge  and allows Actionbase to derive multiple query paths from it.

[Result]
zipdoki is now following j4rami.

<div style="position: relative; white-space: normal">
  <div style="position: absolute; top: 0; left: 0; right: 0; bottom: 0; z-index: 10"></div>
  <div class="profile-header-section" style="align-items: center; margin: 0">
    <div class="profile-avatar-container">
      <div class="profile-avatar-large" style="background: linear-gradient(135deg, rgb(242 209 168) 0%, rgb(223 134 44) 100%)">
        <span class="profile-icon">
          <img src="https://avatars.githubusercontent.com/u/382000?v=4" />
        </span>
      </div>
    </div>
    <div class="profile-right-section" style="gap: 8px">
      <div class="profile-username-row">
        <h2 class="profile-username">j4rami</h2>
        <button class="icon-btn-menu">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <circle cx="12" cy="5" r="1.5"/>
            <circle cx="12" cy="12" r="1.5"/>
            <circle cx="12" cy="19" r="1.5"/>
          </svg>
        </button>
      </div>
      <div class="profile-actions">
        <button class="action-button-following-default action-button-following" style="white-space: nowrap">
          Following
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M6 9l6 6 6-6"/></svg>
        </button>
        <button class="action-button-primary">Message</button>
      </div>
    </div>
  </div>
</div>
`,
    element: "[id='run-command-btn']",
    command: {
      content: `mutate user_follows \\
--type INSERT \\
--table user_follows \\
--source zipdoki \\
--target j4rami \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'
`
    },
    navigation: {
      next: {waitFor: ["[id='btn-profile-following']", "[id='run-command-btn']"]},
      prev: {to: '/search', waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyFollowCreated,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 9
  {
    index: 9,
    title: "Get Follow Relationship",
    description: `Use a Get query to verify that the follow interaction exists.

This query checks for the presence of a specific edge between two user nodes.`,
    element: "[id='run-command-btn']",
    command: {content: 'get user_follows --source zipdoki --target j4rami'},
    navigation: {
      next: {waitFor: ["[id='run-command-btn']"]},
      prev: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyFollowCreated,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 10
  {
    index: 10,
    title: "Check Follower Count",
    description: `Check the follower count for j4rami.

Actionbase derives this value directly—no explicit counters are defined.`,
    element: "[id='run-command-btn']",
    command: {content: 'count user_follows --start j4rami --direction IN'},
    navigation: {
      next: {waitFor: ["[id='run-command-btn']"]},
      prev: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyFollowCreated,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 11
  {
    index: 11,
    title: "Scan Followers",
    description: `Traverse the interaction graph to list users who are following j4rami.

This demonstrates how Actionbase supports common traversal patterns over interaction edges.

[Result]
Merlin has one follower.

<div style="position: relative; white-space: normal">
  <div style="position: absolute; top: 0; left: 0; right: 0; bottom: 0; z-index: 10"></div>
  <div class="followers-list">
    <div class="follower-item" style="padding: 8px 0">
      <div class="follower-info" style="cursor: default">
        <div class="follower-avatar" style="background: linear-gradient(135deg, rgb(224 223 222) 0%, rgb(243 125 144) 100%)">
          <img src="https://avatars.githubusercontent.com/u/112409928?v=4" alt="zipdoki" />
        </div>
        <div class="follower-details" style="cursor: default">
          <div class="follower-username">zipdoki</div>
          <div class="follower-name">Dokyung Lee</div>
        </div>
      </div>
      <div class="follow-action-btn following" style="cursor: default">Following</div>
    </div>
  </div>
</div>
`,
    element: "[id='run-command-btn']",
    command: {content: 'scan user_follows --start j4rami --index created_at_desc --direction IN'},
    navigation: {
      next: {},
      prev: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyFollowCreated,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 12
  {
    index: 12,
    titleNumber: '5',
    title: "Likes",
    description: `In this step, you'll work with like interactions.

Likes are modeled as interactions between a user node and a post node, following the same graph-based principles as follows.`,
    navigation: {
      next: {to: '/post/1', waitFor: ["[id='btn-likes']", "[id='run-command-btn']"]},
      prev: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyFollowCreated,
    popover: {side: 'over', align: 'start'},
  },
  // Step 13
  {
    index: 13,
    title: "zipdoki Likes j4rami's Post",
    description: `Write a like interaction between zipdoki and one of j4rami's posts.

[Result]
zipdoki liked j4rami's post.

<div style="position: relative; white-space: normal">
  <div style="position: absolute; top: 0; left: 0; right: 0; bottom: 0; z-index: 10"></div>
  <div class="post-detail-container" style="padding: 0">
    <div class="post-detail-header" style="padding: 0 0 12px 0">
      <div class="author-info" style="cursor: default; gap: 12px; display: flex; align-items: center">
        <div class="author-avatar" style="background: linear-gradient(135deg, rgb(242 209 168) 0%, rgb(223 134 44) 100%); width: 32px; height: 32px">
          <img src="https://avatars.githubusercontent.com/u/382000?v=4" alt="j4rami" />
        </div>
        <span class="author-name">j4rami</span>
      </div>
    </div>
    <div class="post-detail-image" style="aspect-ratio: 1/1">
      <div class="image-carousel">
        <div class="image-carousel-track">
          <div class="image-content">
            <span class="main-icon">
              <img src="/images/0e7fe655-f65e-4413-a3d2-299fcfa40de0.jpg" alt="post" />
            </span>
          </div>
        </div>
      </div>
    </div>
    <div class="post-detail-actions" style="padding: 4px 0 0; margin: 0">
      <div class="action-buttons-wrapper" style="margin: 0; display: flex; justify-content: space-between; align-items: center">
        <div class="actions-left" style="display: flex; align-items: center; gap: 8px">
          <div class="action-icon liked" style="padding: 8px; cursor: default; color: #ff3040">
            <svg viewBox="0 0 24 24" fill="#ff3040" stroke="#ff3040" stroke-width="2" style="width: 24px; height: 24px; display: block">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
          </div>
          <div class="action-icon" style="padding: 8px; cursor: default">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" style="width: 24px; height: 24px; display: block">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
          </div>
          <div class="action-icon" style="padding: 8px; cursor: default">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 24px; height: 24px; display: block">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </div>
        </div>
        <div class="action-icon action-bookmark" style="padding: 8px; cursor: default">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 24px; height: 24px; display: block">
            <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
      </div>
    </div>
    <div class="likes-count-section" style="padding: 4px 0 0">
      <span class="likes-text">2 likes</span>
    </div>
  </div>
</div>
`,
    element: "[id='run-command-btn']",
    command: {
      content: `mutate user_likes \\
--type INSERT \\
--table user_likes \\
--source zipdoki \\
--target 1 \\
--version __CURRENT_TIMESTAMP__ \\
--properties '{
    "createdAt": __CURRENT_TIMESTAMP__
}'`
    },
    navigation: {
      next: {waitFor: ["[id='run-command-btn']"]},
      prev: {to: '/profile/j4rami'},
    },
    verifier: verifyLikeCreated,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 14
  {
    index: 14,
    title: "Get Likes",
    description: `Use a Get query to confirm that the like interaction exists between the user and the post.`,
    element: "[id='run-command-btn']",
    command: {content: 'get user_likes --source zipdoki --target 1'},
    navigation: {
      next: {},
      prev: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyLikeCreated,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 15
  {
    index: 15,
    title: "Explore Further",
    description: `Just like follows, you can check the count or scan for likes. Give it a try later!`,
    navigation: {
      next: {to: '/'},
      prev: {waitFor: ["[id='run-command-btn']"]},
    },
    verifier: verifyAllTablesExist,
    popover: {side: 'over', align: 'start'},
  },
  // Step 16
  {
    index: 16,
    title: "Feed",
    description: `As with follows, you can also:

Query derived like counts
Traverse users who liked a post
These patterns are supported directly by the interaction graph.`,
    element: "[class='mobile-frame']",
    navigation: {
      prev: {to: '/post/1'},
    },
    popover: {side: 'over', align: 'start'},
  },
  // Step 17
  {
    index: 17,
    titleNumber: '6',
    title: "End",
    description: `At this point, you've created only follow and like interactions.

Even with this limited set of interactions, you can already construct feed-style queries by traversing the interaction graph.
This reflects a common social application pattern and aligns naturally with Actionbase's graph-based design.`,
    popover: {side: 'bottom', align: 'start'},
  },
  // Step 18
  {
    index: 18,
    titleNumber: '7',
    title: "Goodbye!",
    description: `The application is now open for further exploration.

Follow and like features are available, and additional features can be built by extending the same interaction patterns introduced in this guide.

Thank you for trying Actionbase.`,
    popover: {side: 'over', align: 'center'},
  },
];

export const getStepConfig = (index: number): StepConfig | undefined => {
  return stepsConfig.find(step => step.index === index);
};

export const getStepCommand = (index: number): CommandConfig | undefined => {
  return getStepConfig(index)?.command;
};

export const getNextNavigation = (index: number): NavigationConfig | undefined => {
  return getStepConfig(index)?.navigation?.next;
};

export const getPrevNavigation = (index: number): NavigationConfig | undefined => {
  return getStepConfig(index)?.navigation?.prev;
};

export const getStepVerifier = (index: number): (() => Promise<boolean>) | undefined => {
  return getStepConfig(index)?.verifier;
};

export const generateBreadCrumbSteps = (): BreadCrumbStep[] => {
  const result: BreadCrumbStep[] = [];
  let currentParent: BreadCrumbStep | null = null;

  for (const step of stepsConfig) {
    if (step.titleNumber) {
      if (currentParent) {
        result.push(currentParent);
      }
      currentParent = {
        stepIndex: step.index,
        title: step.title ? `${step.title}` : undefined,
        subSteps: [],
      };
    } else if (step.title && currentParent) {
      currentParent.subSteps?.push({
        stepIndex: step.index,
        title: step.title,
      });
    }
  }

  if (currentParent) {
    result.push(currentParent);
  }

  return result;
};
