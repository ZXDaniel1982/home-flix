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
