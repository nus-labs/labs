# This gcd function is from https://gist.github.com/ErbaAitbayev/8f491c04af5fc1874e2b0744965a732b

def extended_gcd(aa, bb):
    lastremainder, remainder = abs(aa), abs(bb)
    x, lastx, y, lasty = 0, 1, 1, 0
    while remainder:
        lastremainder, (quotient, remainder) = remainder, divmod(lastremainder, remainder)
        x, lastx = lastx - quotient*x, x
        y, lasty = lasty - quotient*y, y
    return lastremainder, lastx * (-1 if aa < 0 else 1), lasty * (-1 if bb < 0 else 1)

correct_result = 662
faulty_result = 455
p = 29
q = 23
retrieved_prime = extended_gcd(correct_result - faulty_result, p * q)[0]

print("The correct result is : " + str(correct_result))
print("The faulty result is : " + str(faulty_result))
print("The first prime number is : " + str(p))
print("The second prime number is : " + str(q))
print()
print("The retrieved prime number is : " + str(retrieved_prime))
if retrieved_prime == p or retrieved_prime == q:
    print("The attack is successful")
else:
    print("The attack is unsuccessful")
