package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.MilestoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class EscrowService {

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private ProjectRepository farmProjectRepository;

    /**
     * This method releases a portion of the investment funds to the Farmer.
     * It should only be called after the Village Head verifies a Milestone as "COMPLETED".
     */
    @Transactional
    public void releaseMilestoneFunds(Long milestoneId) {
        // 1. Find the Milestone
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new RuntimeException("Milestone not found with ID: " + milestoneId));

        // 2. Safety Check: Is it actually completed?
        // Note: Make sure your Milestone entity has a 'status' field.
        if (!"COMPLETED".equalsIgnoreCase(milestone.getStatus())) {
            throw new RuntimeException("Funds Locked: The Village Head must mark this milestone as COMPLETED first.");
        }

        // 3. Prevent Double Payouts
        if (milestone.isFundsReleased()) {
            throw new RuntimeException("Error: Funds for this milestone have already been released.");
        }

        // 4. Get the FarmProject (The "Company/Land")
        FarmProject project = milestone.getFarmProject();

        // 5. Calculate Payout
        // Example: If Milestone releasePercentage is 20.0 and Target is 1,00,000, payout is 20,000.
        if (milestone.getReleasePercentage() == null || milestone.getReleasePercentage() <= 0) {
            throw new RuntimeException("Error: Milestone does not have a valid release percentage defined.");
        }

        BigDecimal payout = project.getTargetAmount()
                .multiply(BigDecimal.valueOf(milestone.getReleasePercentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // 6. Move the Money
        // We subtract from the locked 'escrowBalance' and add to 'releasedToFarmer'
        project.setEscrowBalance(project.getEscrowBalance().subtract(payout));
        project.setReleasedToFarmer(project.getReleasedToFarmer().add(payout));

        // 7. Mark Milestone as Paid
        milestone.setFundsReleased(true);

        // 8. Save Changes to Database
        farmProjectRepository.save(project);
        milestoneRepository.save(milestone);

        System.out.println("LOG: Successfully released ₹" + payout + " to Farmer for Milestone: " + milestone.getTitle());
    }
}
