import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockAuthResponse = { token: 'jwt-token', userId: 1, username: 'alice' };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'register']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideRouter([{ path: 'dashboard', component: LoginComponent }])
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('debería crearse el componente', () => {
    expect(component).toBeTruthy();
  });

  it('debería iniciar en modo login', () => {
    expect(component.mode()).toBe('login');
  });

  it('toggleMode: debería cambiar de login a register', () => {
    component.toggleMode();
    expect(component.mode()).toBe('register');
  });

  it('toggleMode: debería limpiar el mensaje de error al cambiar modo', () => {
    component.error.set('Algún error previo');
    component.toggleMode();
    expect(component.error()).toBe('');
  });

  it('submit en modo login: debería navegar al dashboard si las credenciales son correctas', () => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));
    spyOn(router, 'navigate');
    component.loginForm = { email: 'alice@test.com', password: 'pass123' };

    component.submit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({ email: 'alice@test.com', password: 'pass123' });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('submit en modo login: debería mostrar error si las credenciales son incorrectas', () => {
    authServiceSpy.login.and.returnValue(throwError(() => new Error('Unauthorized')));
    component.loginForm = { email: 'alice@test.com', password: 'wrong' };

    component.submit();

    expect(component.error()).toBe('Credenciales inválidas. Por favor intenta de nuevo.');
    expect(component.loading()).toBeFalse();
  });

  it('submit en modo register: debería navegar al dashboard si el registro es exitoso', () => {
    authServiceSpy.register.and.returnValue(of(mockAuthResponse));
    spyOn(router, 'navigate');
    component.mode.set('register');
    component.registerForm = { username: 'alice', email: 'alice@test.com', password: 'pass123' };

    component.submit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      username: 'alice', email: 'alice@test.com', password: 'pass123'
    });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('submit en modo register: debería mostrar error si el registro falla', () => {
    authServiceSpy.register.and.returnValue(throwError(() => new Error('Conflict')));
    component.mode.set('register');
    component.registerForm = { username: 'alice', email: 'alice@test.com', password: 'pass123' };

    component.submit();

    expect(component.error()).toBe('Credenciales inválidas. Por favor intenta de nuevo.');
    expect(component.loading()).toBeFalse();
  });

  it('submit: debería activar loading mientras se procesa', () => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));
    component.loginForm = { email: 'alice@test.com', password: 'pass123' };

    component.submit();

    expect(authServiceSpy.login).toHaveBeenCalled();
  });
});
