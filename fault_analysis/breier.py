your_secret_key = "13111d7fe3944a17f307a78b4d2b30c5"
your_correct_ciphertext = "69c4e0d86a7b0430d8cdb78070b4c55a"
filename = 'result.txt'

import matplotlib
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import numpy as np

key_bytes = np.ones(16, dtype=int)

def count(calculated, expected):
    for i in range(0, 32, 2):
        if(calculated[i:i+2] == expected[i:i+2]):
            key_bytes[int(i / 2)] = 0

def calculate(correct, faulty):
    correct = int(correct, 16)
    faulty = int(faulty, 16)
    result = correct ^ faulty
    return str(format(result, "16x"))

content = open(filename)
y = np.array([16])
line = content.readline().strip()
while(line != ''):
    if(line != "No response from the system"):
        x = calculate(line, your_correct_ciphertext)
        count(x, your_secret_key)
        y = np.append(y, np.sum(key_bytes))
        
    else:
        y = np.append(y, y[-1])
        
    line = content.readline().strip()

fig, ax = plt.subplots()
x = np.arange(0, len(y))
plt.grid()
plt.gca().xaxis.set_major_locator(mticker.MultipleLocator(1))
plt.ylim(0, 17)
ax.set_title('Laser Fault Injection Results')
ax.set_ylabel('Secret Key Bytes')
ax.set_xlabel('Number of Experiments')
ax.plot(x, y)
