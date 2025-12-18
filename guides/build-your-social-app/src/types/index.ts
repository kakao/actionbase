export interface User {
  id: string;
  isMe: boolean;
  icon: string;
  gradient: string;
  name: string;
  message: string;
}

export interface PostDetail {
  id: number;
  imageUrls: string[];
  content: string;
}

export interface UserPost {
  owner: User;
  id: number;
  images: string[];
  content: string;
  likes: number;
  createdAt: number | string;
  isLiked?: boolean;
}

export interface TouchPosition {
  start: number | null;
  end: number | null;
}

