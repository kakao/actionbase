import {User, PostDetail} from '../types';

export const me: User = {
  id: 'doki',
  isMe: true,
  icon: '🐶',
  gradient: 'linear-gradient(135deg, rgb(224 223 222) 0%, rgb(243 125 144) 100%)',
  name: "Doki",
  message: "Making every day an adventure 🚀",
}

export const users: User[] = [
  me,
  {
    id: 'emeth',
    isMe: false,
    icon: '🐱',
    gradient: 'linear-gradient(135deg, rgb(254 237 157) 0%, rgb(221 168 1) 100%)',
    name: "Emeth",
    message: "Just a human exploring the world 🌍",
  },
  {
    id: 'merlin',
    isMe: false,
    icon: '🦦',
    gradient: 'linear-gradient(135deg, rgb(242 209 168) 0%, rgb(223 134 44) 100%)',
    name: "Merlin",
    message: "Coffee lover ☕ | Dreamer 💭 | Doer 💪",
  },
  {
    id: 'dave',
    isMe: false,
    icon: '🐹',
    gradient: 'linear-gradient(135deg, rgb(255 217 147) 0%, rgb(232 165 176) 100%)',
    name: "Dave",
    message: "Creating my own sunshine ☀️",
  },
  {
    id: 'chan',
    isMe: false,
    icon: '🐰',
    gradient: 'linear-gradient(135deg, rgb(236 224 223) 0%, rgb(193 182 181) 100%)',
    name: "Chan",
    message: "Life’s short, make it sweet 🍭",
  },
  {
    id: 'rhyno',
    isMe: false,
    icon: '🦊',
    gradient: 'linear-gradient(135deg, rgb(226 200 173) 0%, rgb(255 156 24) 100%)',
    name: "Rhyno",
    message: "Chasing dreams & catching vibes ✨",
  },
  {
    id: 'eden',
    isMe: false,
    icon: '🐸',
    gradient: 'linear-gradient(135deg, rgb(234 249 131) 0%, rgb(100 185 78) 100%)',
    name: "Eden",
    message: "Collector of memories, not things 📸",
  },
  {
    id: 'ben',
    isMe: false,
    icon: '🐼',
    gradient: 'linear-gradient(135deg, rgb(254 254 254) 0%, rgb(134 135 132) 100%)',
    name: "Ben",
    message: "Fluent in sarcasm and smiles 😏😊",
  },
  {
    id: 'sonu',
    isMe: false,
    icon: '️🐳',
    gradient: 'linear-gradient(135deg, rgb(215 255 255) 0%, rgb(88 189 210) 100%)',
    name: "Sonu",
    message: "Here to inspire & be inspired 💡",
  },
];

export const postDetails: PostDetail[] = [
  {
    id: 1,
    imageUrls: ["/images/0e7fe655-f65e-4413-a3d2-299fcfa40de0.jpg"],
    content: "Collecting memories, one day at a time."
  },
  {
    id: 2,
    imageUrls: ["/images/e1b07e3c-a718-47da-a51f-b792caa2f505.jpg", "/images/22db8476-d099-43f0-9e30-b5143a0bbbe1.jpg"],
    content: "Living today with a grateful heart."
  },
  {
    id: 3,
    imageUrls: ["/images/4df96af3-6e25-434e-a5cf-2d9493fb1a5d.jpg"],
    content: "Here’s to new days and new stories."
  },
  {
    id: 4,
    imageUrls: ["/images/eed15e93-07af-4f13-93bd-5700f38f049e.jpg"],
    content: "Finding joy in the little things."
  },
  {
    id: 5,
    imageUrls: ["/images/8ab71e9c-6f2b-4db6-bb1f-21936f3d04a6.jpg"],
    content: "Too cute to handle."
  },
];
