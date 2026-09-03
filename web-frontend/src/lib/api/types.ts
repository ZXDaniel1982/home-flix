export interface AuthenticateByNameRequest {
	Username: string;
	Pw: string;
}

export interface User {
	Id: string;
	Name: string;
}

export interface AuthenticationResult {
	User: User;
	AccessToken: string;
	ServerId: string;
}

export interface UserData {
	PlaybackPositionTicks?: number;
	PlayedPercentage?: number;
}

export interface BaseItemDto {
	Id: string;
	Name: string;
	Type?: string;
	SeriesId?: string;
	ProductionYear?: number;
	IndexNumber?: number;
	ImageTags?: Record<string, string>;
	BackdropImageTags?: string[];
	Overview?: string;
	RunTimeTicks?: number;
	Genres?: string[];
	UserData?: UserData;
}

export interface QueryResult<T> {
	Items?: T[];
	TotalRecordCount?: number;
}

export interface MediaSourceInfo {
	Id: string;
	Path?: string;
	Container?: string;
	SupportsDirectPlay?: boolean;
	SupportsDirectStream?: boolean;
}

export interface PlaybackInfoResponse {
	MediaSources?: MediaSourceInfo[];
}
