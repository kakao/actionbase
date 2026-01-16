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
  STEP_10: "Get Follow Relationship",
  STEP_11: "Check Follower Count",
  STEP_13: "Scan Followers",
  STEP_15: "Likes",
  STEP_16: "zipdoki Likes j4rami's Post",
  STEP_18: "Get Likes",
  STEP_19: "Explore Further",
  STEP_20: "Feed",
  STEP_21: "End 🎉",
  STEP_22: "Goodbye!"
}

export const DESCRIPTION = {
  STEP_0: `Welcome to the Actionbase hands-on guide.

In this guide, you'll work with a small but realistic social media dataset and build interaction features step by step.
Each step introduces a common social pattern and shows how Actionbase supports it through simple data operations.`,
  STEP_1: `<img class="profile-image" src="${me.avatar}" /><p class="profile-name">zipdoki</p>Let’s assume you are zipdoki.`,
  STEP_2: `Before we begin, let's prepare the environment for this guide.

To help you focus on interaction patterns rather than setup details, we provide a preset dataset and a ready-to-use database context.`,
  STEP_3: `Load the prepared data for this hands-on.

This step creates a database and tables with sample data commonly used in social media applications.

<pre>
\`\`\`
The following resources have been created:

- Database: social
- Tables with preset data:
  - user_posts
  - user_likes
\`\`\`
</pre>`,
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

This single mutation adds an edge  and allows Actionbase to derive multiple query paths from it.`,
  STEP_9: "zipdoki is now following j4rami.",
  STEP_10: `Use a Get query to verify that the follow interaction exists.

This query checks for the presence of a specific edge between two user nodes.`,
  STEP_11: `Check the follower count for j4rami.

Actionbase derives this value directly—no explicit counters are defined.`,
  STEP_12: `j4rami has one follower.`,
  STEP_13: `Traverse the interaction graph to list users who are following j4rami.

This demonstrates how Actionbase supports common traversal patterns over interaction edges.`,
  STEP_14: "(pop up)",
  STEP_15: `In this step, you'll work with like interactions.

Likes are modeled as interactions between a user node and a post node, following the same graph-based principles as follows.`,
  STEP_16: `Write a like interaction between zipdoki and one of j4rami's posts.`,
  STEP_17: `zipdoki liked j4rami's post.`,
  STEP_18: `Use a Get query to confirm that the like interaction exists between the user and the post.`,
  STEP_19: `Just like follows, you can check the count or scan for likes. Give it a try later!`,
  STEP_20: `As with follows, you can also:

Query derived like counts
Traverse users who liked a post
These patterns are supported directly by the interaction graph.`,
  STEP_21: `At this point, you've created only follow and like interactions.

Even with this limited set of interactions, you can already construct feed-style queries by traversing the interaction graph.
This reflects a common social application pattern and aligns naturally with Actionbase's graph-based design.`,
  STEP_22: `The application is now open for further exploration.

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
        stepIndex: 10,
        title: TITLE.STEP_10,
      },
      {
        stepIndex: 11,
        title: TITLE.STEP_11,
      },
      {
        stepIndex: 13,
        title: TITLE.STEP_13,
      }
    ]
  },
  {
    stepIndex: 15,
    title: TITLE.STEP_15,
    subSteps: [
      {
        stepIndex: 16,
        title: TITLE.STEP_16,
      },
      {
        stepIndex: 18,
        title: TITLE.STEP_18,
      },
      {
        stepIndex: 19,
        title: TITLE.STEP_19,
      }
    ]
  },
  {
    stepIndex: 20,
    title: TITLE.STEP_20
  },
  {
    stepIndex: 21,
    title: TITLE.STEP_21
  },
  {
    stepIndex: 22,
    title: TITLE.STEP_22
  },
];
