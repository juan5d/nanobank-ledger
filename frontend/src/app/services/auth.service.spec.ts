import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('login should store token and set isAuthenticated', () => {
    service.login({ email: 'test@test.com', password: 'pass123' }).subscribe(res => {
      expect(res.token).toBe('jwt-token');
      expect(service.isAuthenticated()).toBeTrue();
      expect(localStorage.getItem('nanobank_token')).toBe('jwt-token');
    });

    const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'jwt-token', userId: 1, username: 'alice' });
  });

  it('register should store token and set isAuthenticated', () => {
    service.register({ username: 'alice', email: 'alice@test.com', password: 'pass123' }).subscribe(res => {
      expect(res.token).toBe('new-token');
      expect(service.isAuthenticated()).toBeTrue();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/auth/register');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'new-token', userId: 1, username: 'alice' });
  });

  it('logout should clear token and set isAuthenticated to false', () => {
    localStorage.setItem('nanobank_token', 'some-token');
    service.logout();
    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('nanobank_token')).toBeNull();
  });

  it('getToken should return stored token', () => {
    localStorage.setItem('nanobank_token', 'stored-token');
    expect(service.getToken()).toBe('stored-token');
  });

  it('getToken should return null when no token', () => {
    expect(service.getToken()).toBeNull();
  });
});
