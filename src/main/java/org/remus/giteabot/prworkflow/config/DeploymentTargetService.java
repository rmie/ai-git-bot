package org.remus.giteabot.prworkflow.config;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.admin.BotRepository;
import org.remus.giteabot.admin.EncryptionService;
import org.remus.giteabot.prworkflow.deployment.DeploymentStrategyType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD for {@link DeploymentTarget} rows. Encrypts the {@code configJson}
 * column on save via {@link EncryptionService} so the cipher text is what
 * Hibernate persists; callers receive cleartext through {@link #findById}
 * and {@link #findAll}.
 *
 * <p>Guards:</p>
 * <ul>
 *     <li>Name uniqueness (case-sensitive — DB UK).</li>
 *     <li>Cannot delete a target that is still referenced by a bot.</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
public class DeploymentTargetService {

    private final DeploymentTargetRepository repository;
    private final BotRepository botRepository;
    private final EncryptionService encryptionService;

    public DeploymentTargetService(DeploymentTargetRepository repository,
                                   BotRepository botRepository,
                                   EncryptionService encryptionService) {
        this.repository = repository;
        this.botRepository = botRepository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public List<DeploymentTarget> findAll() {
        List<DeploymentTarget> all = repository.findAll();
        all.forEach(this::decryptInPlace);
        return all;
    }

    @Transactional(readOnly = true)
    public Optional<DeploymentTarget> findById(Long id) {
        return repository.findById(id).map(target -> {
            decryptInPlace(target);
            return target;
        });
    }

    public DeploymentTarget save(DeploymentTarget target) {
        if (target.getName() == null || target.getName().isBlank()) {
            throw new IllegalArgumentException("Deployment target name must not be blank");
        }
        if (target.getStrategyType() == null) {
            throw new IllegalArgumentException("Deployment target strategy type must not be null");
        }
        if (target.getConfigJson() == null || target.getConfigJson().isBlank()) {
            // Allow empty {} to be persisted but never null — keeps NOT NULL constraint happy.
            target.setConfigJson("{}");
        }
        if (target.getTimeoutSeconds() <= 0) {
            target.setTimeoutSeconds(600);
        }
        boolean nameTaken = target.getId() == null
                ? repository.existsByName(target.getName())
                : repository.existsByNameAndIdNot(target.getName(), target.getId());
        if (nameTaken) {
            throw new IllegalArgumentException(
                    "A deployment target named '" + target.getName() + "' already exists");
        }
        String plaintext = target.getConfigJson();
        target.setConfigJson(encryptionService.encrypt(plaintext));
        DeploymentTarget saved = repository.save(target);
        // Return cleartext to the caller — never leak the cipher text upwards.
        saved.setConfigJson(plaintext);
        return saved;
    }

    public void deleteById(Long id) {
        DeploymentTarget target = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Unknown deployment target id=" + id));
        long inUse = botRepository.findAll().stream()
                .filter(bot -> bot.getDeploymentTarget() != null
                        && bot.getDeploymentTarget().getId().equals(target.getId()))
                .count();
        if (inUse > 0) {
            throw new IllegalStateException(
                    "Cannot delete deployment target '" + target.getName() + "': still used by "
                            + inUse + " bot(s)");
        }
        repository.deleteById(id);
    }

    /**
     * Returns the set of strategy types the operator can pick from in the
     * admin UI. Currently filtered to the strategies that ship with M3
     * ({@code WEBHOOK} and {@code STATIC}); the remaining values exist in the
     * enum so the schema is forward-compatible.
     */
    @Transactional(readOnly = true)
    public List<DeploymentStrategyType> availableStrategyTypes() {
        return List.of(DeploymentStrategyType.WEBHOOK, DeploymentStrategyType.STATIC);
    }

    private void decryptInPlace(DeploymentTarget target) {
        if (target.getConfigJson() != null) {
            target.setConfigJson(encryptionService.decrypt(target.getConfigJson()));
        }
    }
}

