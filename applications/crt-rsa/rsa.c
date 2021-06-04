#include <stdlib.h>
#include <math.h>

#pragma GCC push_options
#pragma GCC optimize ("O0")
void finish(int result){
    	int temp;
    	temp = result + 99; // this is just used as a flag for the testbench
}
#pragma GCC pop_options


int findGCD(int n1, int n2) {
	int i, gcd;

	for(i = 1; i <= n1 && i <= n2; ++i) {
		if(n1 % i == 0 && n2 % i == 0)
			gcd = i;
	}

	return gcd;
}

int powMod(int a, int b, int n) {
	long long x = 1, y = a;
	while (b > 0) {
		if (b % 2 == 1)
			x = (x * y) % n;
		y = (y * y) % n; // Squaring the base
		b /= 2;
	}

	return x % n;
}

//Copied from https://www.geeksforgeeks.org/multiplicative-inverse-under-modulo-m/
int modInverse(int a, int m) 
{ 
    a = a%m; 
    for (int x=1; x<m; x++) 
       if ((a*x) % m == 1) 
          return x; 
} 

int main(int argc, char* argv[]) {
	int p, q;
	p = 29;
	q = 23;
	int n, phin;

	int data, cipher, decrypt;
	data = 123;

	n = p * q;

	phin = (p - 1) * (q - 1);

	int e = 0;
	for (e = 5; e <= 100; e++) {
		if (findGCD(phin, e) == 1)
			break;
	}
	
	int d = 0;
	for (d = e + 1; d <= 100; d++) {
		if ( ((d * e) % phin) == 1)
			break;
	}

	int dp = d % (p - 1);
	int dq = d % (q - 1);
	int sp = powMod(data, dp, p);
	int sq = powMod(data, dq, q);
	int iq = modInverse(q, p);
	int s = sq + q * (iq * (sp - sq) % p);
	finish(s);
	return 0;
}
