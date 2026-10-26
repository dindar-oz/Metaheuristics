package problem.pecto;

import problem.ObjectiveFunction;
import problem.ObjectiveType;
import representation.IntegerPermutation;
import static problem.pecto.PECTOModel2.*;


public class PECTOMinimumTimeOF implements ObjectiveFunction<IntegerPermutation, PECTOModel2> {
    @Override
    public ObjectiveType type() {
        return ObjectiveType.Minimization;
    }

    @Override
    public double value(PECTOModel2 pectoModel, IntegerPermutation integerPermutation) {
        double totalDistance = 0.0;
        double[][] distanceMatrix = pectoModel.getDistanceMatrix();

        for (int i = 0; i < integerPermutation.size() - 1; i++) {
            int from = integerPermutation.get(i);
            int to = integerPermutation.get(i + 1);

            double urgencyWeight;

            ParticipantType fromParticipant = pectoModel.getParticipantType(from);
            ParticipantType toParticipant = pectoModel.getParticipantType(to);

            if (fromParticipant.equals(ParticipantType.AMBULANCE) && toParticipant.equals(ParticipantType.PATIENT)) {
                urgencyWeight = pectoModel.getPatientUrgencyMultiplier(to);
            } else if (fromParticipant.equals(ParticipantType.PATIENT)) {
                urgencyWeight = pectoModel.getPatientUrgencyMultiplier(from);
            } else if (fromParticipant.equals(ParticipantType.HOSPITAL)) {
                if (toParticipant.equals(ParticipantType.PATIENT)) {
                    urgencyWeight = pectoModel.getPatientUrgencyMultiplier(to);
                } else {
                    urgencyWeight = 0;
                }
            } else {
                urgencyWeight = 0;
            }

            totalDistance += urgencyWeight * distanceMatrix[from][to];
        }

        return totalDistance;
    }
}
