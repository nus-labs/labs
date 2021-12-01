import matplotlib.pyplot as plt; plt.rcdefaults()
import numpy as np
import matplotlib.pyplot as plt

f = open("test4.txt", "r")
f2 = open("test.txt", "r")
f3 = open("test3.txt", "r")

result = f.read()
result2 = f2.read()
result3 = f3.read()

expected = "100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100100010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010010001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001001"

correct = 0
result_label_correct_rate_ori = [0, 0, 0]
for i in range(0, len(expected), 3):
    if expected[i : i + 3] == result2[i : i + 3]:
        correct += 1
        result_label_correct_rate_ori[result2[i : i + 3].find('1')] += 1
print(str(round(correct * 100 / (len(expected) / 3), 2)) + "% correct")

correct = 0
result_label_correct_rate = [0, 0, 0]
for i in range(0, len(expected), 3):
    if expected[i : i + 3] == result[i : i + 3]:
        correct += 1
        result_label_correct_rate[result[i : i + 3].find('1')] += 1
print(str(round(correct * 100 / (len(expected) / 3), 2)) + "% correct")

correct = 0
result_label_correct_rate_out = [0, 0, 0]
for i in range(0, len(expected), 3):
    if expected[i : i + 3] == result3[i : i + 3]:
        correct += 1
        result_label_correct_rate_out[result3[i : i + 3].find('1')] += 1
print(str(round(correct * 100 / (len(expected) / 3), 2)) + "% correct")

expected_label_count = [0, 0, 0]
for i in range(0, len(expected), 3):
    if expected[i] == '1':
        expected_label_count[0] += 1
    elif expected[i + 1] == '1':
        expected_label_count[1] += 1
    elif expected[i + 2] == '1':
        expected_label_count[2] += 1

result_label_count = [0, 0, 0]
for i in range(0, len(result), 3):
    if result[i] == '1':
        result_label_count[0] += 1
    elif result[i + 1] == '1':
        result_label_count[1] += 1
    elif result[i + 2] == '1':
        result_label_count[2] += 1

result_label_count_out = [0, 0, 0]
for i in range(0, len(result3), 3):
    if result3[i] == '1':
        result_label_count_out[0] += 1
    elif result3[i + 1] == '1':
        result_label_count_out[1] += 1
    elif result3[i + 2] == '1':
        result_label_count_out[2] += 1

# Code from https://stackoverflow.com/questions/14270391/python-matplotlib-multiple-bars

N = 3
ind = np.arange(N)  # the x locations for the groups
width = 0.27    # the width of the bars

fig = plt.figure()

ax = fig.add_subplot(111)

yvals = expected_label_count
rects1 = ax.bar(ind, yvals, width, color='#9dc3e6')
zvals = result_label_correct_rate_ori
rects2 = ax.bar(ind+width, zvals, width, color='#f4b183')
kvals = result_label_correct_rate
rects3 = ax.bar(ind+width*2, kvals, width, color='#A9D18E')
#E2F0D9

ax.set_ylabel('')
ax.set_xticks(ind+width)
ax.set_xticklabels( ('Label 1', 'Label 2', 'Label 3') )
ax.legend((rects1[0], rects2[0], rects3[0]), ('Ground-truth', 'Inference without faults', 'Inference with faults', 'Output layers'), loc='upper center', bbox_to_anchor=(0.5, -0.1),
          fancybox=True, shadow=False, ncol=4)
# plt.title('Number of Correct Samples from Experiment')

def autolabel(rects):
    for rect in rects:
        h = rect.get_height()
        ax.text(rect.get_x()+rect.get_width()/2., 1.0*h, '%d'%int(h),
                ha='center', va='bottom')

autolabel(rects1)
autolabel(rects2)
autolabel(rects3)

plt.show()
