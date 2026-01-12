export interface BreadCrumbStep {
  stepIndex: number,
  title?: string;
  isActive?: boolean,
  isCompleted?: boolean,
  subSteps?: BreadCrumbStep[]
}

export const TITLE = {
  STEP_0: "Welcome 🙌🏼",
  STEP_1: "Prepare the Environment",
  STEP_2: "Load preset data",
  STEP_3: "Set Database Context",
  STEP_4: "Review the Prepared Data",
  STEP_5: "Follows",
  STEP_6: "Create the `user_follows` Table",
  STEP_7: "Make doki Follow merlin",
  STEP_9: "Get Follow Relationship",
  STEP_10: "Check Follower Count",
  STEP_12: "Scan Followers",
  STEP_14: "Likes",
  STEP_15: "doki Likes merlin’s Post",
  STEP_17: "Get Likes",
  STEP_18: "Explore Further",
  STEP_19: "Feed",
  STEP_20: "End 🎉",
  STEP_21: "Goodbye!"
}

export const DESCRIPTION = {
  STEP_0: `Welcome to the Actionbase hands-on guide.

In this guide, you'll work with a small but realistic social media dataset and build interaction features step by step.
Each step introduces a common social pattern and shows how Actionbase supports it through simple data operations.`,
  STEP_1: `Before we begin, let's prepare the environment for this guide.

To help you focus on interaction patterns rather than setup details, we provide a preset dataset and a ready-to-use database context.`,
  STEP_2: `Load the prepared data for this hands-on.

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
  STEP_3: `Set the current database context to <pre>\`social\`</pre>.
All subsequent steps in this guide assume this context.`,
  STEP_4: `Before adding new interactions, take a moment to review the prepared data.

The dataset includes users and posts represented as nodes, along with existing interactions such as likes.
Rather than focusing on schema definitions, this guide emphasizes how Actionbase builds queryable relationships directly from interaction data.`,
  STEP_5: `In this step, you'll walk through an interactive flow to create and query follow relationships between users.

Follow relationships are a core feature in most social applications and serve as a good introduction to Actionbase's interaction model.`,
  STEP_6: `Create a <pre>\`user_follows\`</pre> table to store follow interaction between users.

Each edge represents a single interaction: one user follows another.`,
  STEP_7: `Write an interaction where doki follows merlin.

This single mutation adds an edge  and allows Actionbase to derive multiple query paths from it.`,
  STEP_8: "doki is now following merlin.",
  STEP_9: `Use a Get query to verify that the follow interaction exists.

This query checks for the presence of a specific edge between two user nodes.`,
  STEP_10: `Check the follower count for merlin.

Actionbase derives this value directly—no explicit counters are defined.`,
  STEP_11: `Merlin has one follower.`,
  STEP_12: `Traverse the interaction graph to list users who are following merlin.

This demonstrates how Actionbase supports common traversal patterns over interaction edges.`,
  STEP_13: "(pop up)",
  STEP_14: `In this step, you’ll work with like interactions.

Likes are modeled as interactions between a user node and a post node, following the same graph-based principles as follows.`,
  STEP_15: `Write a like interaction between doki and one of merlin’s posts.`,
  STEP_16: `doki liked merlin’s post.`,
  STEP_17: `Use a Get query to confirm that the like interaction exists between the user and the post.`,
  STEP_18: `Just like follows, you can check the count or scan for likes. Give it a try later!`,
  STEP_19: `As with follows, you can also:

Query derived like counts
Traverse users who liked a post
These patterns are supported directly by the interaction graph.`,
  STEP_20: `At this point, you’ve created only follow and like interactions.

Even with this limited set of interactions, you can already construct feed-style queries by traversing the interaction graph.
This reflects a common social application pattern and aligns naturally with Actionbase’s graph-based design.`,
  STEP_21: `The application is now open for further exploration.

Follow and like features are available, and additional features can be built by extending the same interaction patterns introduced in this guide.

Thank you for trying Actionbase.`
}

export const breadCrumbSteps: BreadCrumbStep[] = [
  {
    stepIndex: 0,
    title: TITLE.STEP_0
  },
  {
    stepIndex: 1,
    title: TITLE.STEP_1,
    subSteps: [
      {
        stepIndex: 2,
        title: TITLE.STEP_2
      },
      {
        stepIndex: 3,
        title: TITLE.STEP_3,
      }
    ]
  },
  {
    stepIndex: 4,
    title: TITLE.STEP_4
  },
  {
    stepIndex: 5,
    title: TITLE.STEP_5,
    subSteps: [
      {
        stepIndex: 6,
        title: TITLE.STEP_6,
      },
      {
        stepIndex: 7,
        title: TITLE.STEP_7,
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
        stepIndex: 12,
        title: TITLE.STEP_12,
      }
    ]
  },
  {
    stepIndex: 14,
    title: TITLE.STEP_14,
    subSteps: [
      {
        stepIndex: 15,
        title: TITLE.STEP_15,
      },
      {
        stepIndex: 17,
        title: TITLE.STEP_17,
      },
      {
        stepIndex: 18,
        title: TITLE.STEP_18,
      }
    ]
  },
  {
    stepIndex: 19,
    title: TITLE.STEP_19
  },
  {
    stepIndex: 20,
    title: TITLE.STEP_20
  },
  {
    stepIndex: 21,
    title: TITLE.STEP_21
  },
];
