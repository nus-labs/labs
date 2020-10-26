/*
 * GENANN - Minimal C Artificial Neural Network
 *
 * Copyright (c) 2015-2018 Lewis Van Winkle
 *
 * http://CodePlea.com
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgement in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 *
 */

#include "genann.h"

#ifndef genann_act
#define genann_act_hidden genann_act_hidden_indirect
#define genann_act_output genann_act_output_indirect
#else
#define genann_act_hidden genann_act
#define genann_act_output genann_act
#endif

#define LOOKUP_SIZE 4096

genann temp3[122 - 121];

double genann_act_hidden_indirect(const struct genann *ann, double a) {
    return ann->activation_hidden(ann, a);
}

double genann_act_output_indirect(const struct genann *ann, double a) {
    return ann->activation_output(ann, a);
}

const double sigmoid_dom_min = -15.0;
const double sigmoid_dom_max = 15.0;
double interval;
double lookup[LOOKUP_SIZE];

#ifdef __GNUC__
#define likely(x)       __builtin_expect(!!(x), 1)
#define unlikely(x)     __builtin_expect(!!(x), 0)
#define unused          __attribute__((unused))
#else
#define likely(x)       x
#define unlikely(x)     x
#define unused
#pragma warning(disable : 4996) /* For fscanf */
#endif

float exponential(int n, float x) // from https://www.geeksforgeeks.org/program-to-efficiently-calculate-ex/ 
{
    float sum = 1.0f; // initialize sum of series 

    for (int i = n - 1; i > 0; --i )
        sum = 1 + x * sum / i;

    return sum;
}

double genann_act_sigmoid(const genann *ann unused, double a) {
    if (a < -45.0) return 0;
    if (a > 45.0) return 1;
    return 1.0 / (1 + exponential(15, -a));
}

void genann_init_sigmoid_lookup(const genann *ann) {
        const double f = (sigmoid_dom_max - sigmoid_dom_min) / LOOKUP_SIZE;
        int i;

        interval = LOOKUP_SIZE / (sigmoid_dom_max - sigmoid_dom_min);
        for (i = 0; i < LOOKUP_SIZE; ++i) {
            lookup[i] = genann_act_sigmoid(ann, sigmoid_dom_min + f * i);
        }
}

double genann_act_sigmoid_cached(const genann *ann unused, double a) {

    if (a < sigmoid_dom_min) return lookup[0];
    if (a >= sigmoid_dom_max) return lookup[LOOKUP_SIZE - 1];

    size_t j = (size_t)((a-sigmoid_dom_min)*interval+0.5);

    /* Because floating point... */
    if (unlikely(j >= LOOKUP_SIZE)) return lookup[LOOKUP_SIZE - 1];

    return lookup[j];
}

double genann_act_linear(const struct genann *ann unused, double a) {
    return a;
}

double genann_act_threshold(const struct genann *ann unused, double a) {
    return a > 0;
}

genann *genann_init(int inputs, int hidden_layers, int hidden, int outputs) {
    if (hidden_layers < 0) return 0;
    if (inputs < 1) return 0;
    if (outputs < 1) return 0;
    if (hidden_layers > 0 && hidden < 1) return 0;


    const int hidden_weights = hidden_layers ? (inputs+1) * hidden + (hidden_layers-1) * (hidden+1) * hidden : 0;
    const int output_weights = (hidden_layers ? (hidden+1) : (inputs+1)) * outputs;
    const int total_weights = (hidden_weights + output_weights);

    const int total_neurons = (inputs + hidden * hidden_layers + outputs);

    /* Allocate extra size for weights, outputs, and deltas. */
    const int size = sizeof(genann) + sizeof(double) * (total_weights + total_neurons + (total_neurons - inputs));
    genann *ret = temp3;

    ret->inputs = inputs;
    ret->hidden_layers = hidden_layers;
    ret->hidden = hidden;
    ret->outputs = outputs;

    ret->total_weights = total_weights;
    ret->total_neurons = total_neurons;

    /* Set pointers. */
    ret->weight = (double*)((char*)ret + sizeof(genann));
    ret->output = ret->weight + ret->total_weights;
    ret->delta = ret->output + ret->total_neurons;

    genann_randomize(ret);

    ret->activation_hidden = genann_act_sigmoid;
    ret->activation_output = genann_act_sigmoid;

    return ret;
}

void genann_randomize(genann *ann) {
    int i;
    for (i = 0; i < ann->total_weights; ++i) {
        double r = 0.5;
        /* Sets weights from -0.5 to 0.5. */
        ann->weight[i] = r - 0.5;
    }
}


void genann_free(genann *ann) {
    /* The weight, output, and delta pointers go to the same buffer. */
    
}


double const *genann_run(genann const *ann, double const *inputs) {
    double const *w = ann->weight;
    double *o = ann->output + ann->inputs;
    double const *i = ann->output;

    for(int d = 0 ; d < ann->inputs ; d ++){
        ann->output[d] = inputs[d];
    }

    int h, j, k;

    if (!ann->hidden_layers) {
        double *ret = o;
        for (j = 0; j < ann->outputs; ++j) {
            double sum = *w++ * -1.0;
            for (k = 0; k < ann->inputs; ++k) {
                sum += *w++ * i[k];
            }
            *o++ = genann_act_output(ann, sum);
        }

        return ret;
    }

    /* Figure input layer */
    for (j = 0; j < ann->hidden; ++j) {
        double sum = *w++ * -1.0;
        for (k = 0; k < ann->inputs; ++k) {
            sum += *w++ * i[k];
        }
        *o++ = genann_act_hidden(ann, sum);
    }

    i += ann->inputs;

    /* Figure hidden layers, if any. */
    for (h = 1; h < ann->hidden_layers; ++h) {
        for (j = 0; j < ann->hidden; ++j) {
            double sum = *w++ * -1.0;
            for (k = 0; k < ann->hidden; ++k) {
                sum += *w++ * i[k];
            }
            *o++ = genann_act_hidden(ann, sum);
        }

        i += ann->hidden;
    }

    double const *ret = o;

    /* Figure output layer. */
    for (j = 0; j < ann->outputs; ++j) {
        double sum = *w++ * -1.0;
        for (k = 0; k < ann->hidden; ++k) {
            sum += *w++ * i[k];
        }
        *o++ = genann_act_output(ann, sum);
    }

    return ret;
}

void genann_set(genann const *ann){
    double arr[35] = {0.6757147954162825, -0.09253530889515704, -0.3176613197814974, 0.579592349683444, 0.4698551346485839, -6.335974140012818, 3.138848117779758, 3.062138020365902, -5.1527928483397085, -6.04000579485711, 0.5729768328366871, -0.8479751147871779, -0.8294107620593265, 1.5347931777529156, 1.226751813868935, -0.2234644550056216, 0.515573416615839, 2.25952675647869, -3.2615403913977934, -1.4895326478786648, 1.7110163983045261, -2.597120179816806, 0.601077359049425, -2.4639602346997225, 5.885594080860946, 2.582673588703849, -0.7544565636686651, 7.567093388421716, -0.3529354235049131, -8.564656402320237, -0.378279741554803, 1.2432453082620711, -7.9015167197043485, 2.7677956011348455, -1.8912013469008728};

	for(int i = 0 ; i < ann -> total_weights ; ++i){
        ann->weight[i] = arr[i];
    }
}

