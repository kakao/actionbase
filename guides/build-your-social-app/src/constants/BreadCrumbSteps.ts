export interface BreadCrumbStep {
  stepIndex: number,
  title?: string;
  isActive?: boolean,
  isCompleted?: boolean,
  subSteps?: BreadCrumbStep[]
}

export const breadCrumbSteps: BreadCrumbStep[] = [
  {
    stepIndex: 0,
    title: "Welcome to Actionbase 🙌🏼"
  },
  {
    stepIndex: 1,
    title: "Prepare tutoral",
    subSteps: [
      {
        stepIndex: 2,
        title: "Load preset data",
      },
      {
        stepIndex: 3,
        title: "Use context",
      }
    ]
  },
  {
    stepIndex: 4,
    title: "Check Prepared data"
  },
  {
    stepIndex: 5,
    title: "Follows",
    subSteps: [
      {
        stepIndex: 6,
        title: "Create table",
      },
      {
        stepIndex: 7,
        title: "me -> merlin",
      },
      {
        stepIndex: 8,
        title: "Get follows",
      },
      {
        stepIndex: 9,
        title: "Get follows count",
      },
      {
        stepIndex: 10,
        title: "Scan follows",
      }
    ]
  },
  {
    stepIndex: 11,
    title: "Likes",
    subSteps: [
      {
        stepIndex: 12,
        title: "me -> merlin's post",
      },
      {
        stepIndex: 13,
        title: "me -> merlin's post",
      },
      {
        stepIndex: 14,
        title: "Same as follows",
      }
    ]
  },
  {
    stepIndex: 15,
    title: "Feed"
  },
  {
    stepIndex: 16,
    title: "GoodBye!"
  },
  {
    stepIndex: 17,
    title: "Note"
  },
];
