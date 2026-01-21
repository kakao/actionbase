import {me} from "./dummy";

export interface BreadCrumbStep {
  stepIndex: number,
  title?: string;
  isActive?: boolean,
  isCompleted?: boolean,
  subSteps?: BreadCrumbStep[]
}

export const TITLE = {
  STEP_0: "Welcome 🙌🏼",
  STEP_2: "Prepare the Environment",
  STEP_3: "Load preset data",
  STEP_4: "Set Database Context",
  STEP_5: "Review the Prepared Data",
  STEP_6: "Follows",
  STEP_7: "Create the `user_follows` Table",
  STEP_8: "Make zipdoki Follow j4rami",
  STEP_9: "Get Follow Relationship",
  STEP_10: "Check Follower Count",
  STEP_11: "Scan Followers",
  STEP_12: "Likes",
  STEP_13: "zipdoki Likes j4rami's Post",
  STEP_14: "Get Likes",
  STEP_15: "Explore Further",
  STEP_16: "Feed",
  STEP_17: "End 🎉",
  STEP_18: "Goodbye!"
}

export const DESCRIPTION = {
  STEP_0: `Welcome to the Actionbase hands-on guide.

In this guide, you'll work with a small but realistic social media dataset and build interaction features step by step.
Each step introduces a common social pattern and shows how Actionbase supports it through simple data operations.`,
  STEP_1: `<img class="profile-image" src="${me.avatar}" /><p class="profile-name">zipdoki</p>Let’s assume you are zipdoki.`,
  STEP_2: `Before we begin, let's prepare the environment for this guide.

To help you focus on interaction patterns rather than setup details, we provide a preset dataset and a ready-to-use database context.`,
  STEP_3: `Load the prepared data for this hands-on.

This step creates a database and tables with sample data commonly used in social media applications.`,
  STEP_4: `Set the current database context to <pre>\`social\`</pre>.
All subsequent steps in this guide assume this context.`,
  STEP_5: `Before adding new interactions, take a moment to review the prepared data.

The dataset includes users and posts represented as nodes, along with existing interactions such as likes.
Rather than focusing on schema definitions, this guide emphasizes how Actionbase builds queryable relationships directly from interaction data.`,
  STEP_6: `In this step, you'll walk through an interactive flow to create and query follow relationships between users.

Follow relationships are a core feature in most social applications and serve as a good introduction to Actionbase's interaction model.`,
  STEP_7: `Create a <pre>\`user_follows\`</pre> table to store follow interaction between users.

Each edge represents a single interaction: one user follows another.`,
  STEP_8: `Write an interaction where zipdoki follows j4rami.

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
  STEP_9: `Use a Get query to verify that the follow interaction exists.

This query checks for the presence of a specific edge between two user nodes.`,
  STEP_10: `Check the follower count for j4rami.

Actionbase derives this value directly—no explicit counters are defined.`,
  STEP_11: `Traverse the interaction graph to list users who are following j4rami.

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
  STEP_12: `In this step, you'll work with like interactions.

Likes are modeled as interactions between a user node and a post node, following the same graph-based principles as follows.`,
  STEP_13: `Write a like interaction between zipdoki and one of j4rami's posts.

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
  STEP_14: `Use a Get query to confirm that the like interaction exists between the user and the post.`,
  STEP_15: `Just like follows, you can check the count or scan for likes. Give it a try later!`,
  STEP_16: `As with follows, you can also:

Query derived like counts
Traverse users who liked a post
These patterns are supported directly by the interaction graph.`,
  STEP_17: `At this point, you've created only follow and like interactions.

Even with this limited set of interactions, you can already construct feed-style queries by traversing the interaction graph.
This reflects a common social application pattern and aligns naturally with Actionbase's graph-based design.`,
  STEP_18: `The application is now open for further exploration.

Follow and like features are available, and additional features can be built by extending the same interaction patterns introduced in this guide.

Thank you for trying Actionbase.`
}

export const breadCrumbSteps: BreadCrumbStep[] = [
  {
    stepIndex: 0,
    title: TITLE.STEP_0
  },
  {
    stepIndex: 2,
    title: TITLE.STEP_2,
    subSteps: [
      {
        stepIndex: 3,
        title: TITLE.STEP_3
      },
      {
        stepIndex: 4,
        title: TITLE.STEP_4,
      }
    ]
  },
  {
    stepIndex: 5,
    title: TITLE.STEP_5
  },
  {
    stepIndex: 6,
    title: TITLE.STEP_6,
    subSteps: [
      {
        stepIndex: 7,
        title: TITLE.STEP_7,
      },
      {
        stepIndex: 8,
        title: TITLE.STEP_8,
      },
      {
        stepIndex: 9,
        title: TITLE.STEP_9,
      },
      {
        stepIndex: 10,
        title: TITLE.STEP_10,
      },
      {
        stepIndex: 11,
        title: TITLE.STEP_11,
      }
    ]
  },
  {
    stepIndex: 12,
    title: TITLE.STEP_12,
    subSteps: [
      {
        stepIndex: 13,
        title: TITLE.STEP_13,
      },
      {
        stepIndex: 14,
        title: TITLE.STEP_14,
      },
      {
        stepIndex: 15,
        title: TITLE.STEP_15,
      }
    ]
  },
  {
    stepIndex: 16,
    title: TITLE.STEP_16
  },
  {
    stepIndex: 17,
    title: TITLE.STEP_17
  },
  {
    stepIndex: 18,
    title: TITLE.STEP_18
  },
];
