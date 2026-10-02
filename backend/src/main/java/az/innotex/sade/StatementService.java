package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Hesab çıxarışları: fayl (bazada) + dövr; hərəkətlər öz çıxarışına bağlana bilər */
@Service
public class StatementService {
    private final R.Statements statements;
    private final R.Accounts accounts;
    private final R.Movements movements;
    private final FileService files;

    public StatementService(R.Statements statements, R.Accounts accounts, R.Movements movements, FileService files) {
        this.statements = statements;
        this.accounts = accounts;
        this.movements = movements;
        this.files = files;
    }

    private Dto.StatementRes toDto(E.AccountStatement s) {
        E.StoredFile f = files.meta(s.fileId);
        return new Dto.StatementRes(s.id, s.accountId, s.periodFrom, s.periodTo, s.fileId, f.name, f.sizeBytes, s.note,
                s.createdAt == null ? null : s.createdAt.toString(), movements.countByStatementId(s.id));
    }

    public List<Dto.StatementRes> list(Long accountId) {
        if (!accounts.existsById(accountId)) throw ApiException.notFound("Hesab tapılmadı");
        return statements.findByAccountIdOrderByPeriodFromDescIdDesc(accountId).stream().map(this::toDto).toList();
    }

    @Transactional
    public Dto.StatementRes create(Long accountId, MultipartFile file, LocalDate from, LocalDate to, String note) {
        if (!accounts.existsById(accountId)) throw ApiException.notFound("Hesab tapılmadı");
        if (from == null || to == null) throw ApiException.bad("Çıxarışın dövrü (başlanğıc və son tarix) göstərilməlidir");
        if (to.isBefore(from)) throw ApiException.bad("Son tarix başlanğıc tarixdən əvvəl ola bilməz");
        Dto.FileRes f = files.store(file);
        E.AccountStatement s = new E.AccountStatement();
        s.accountId = accountId;
        s.periodFrom = from;
        s.periodTo = to;
        s.fileId = f.id();
        s.note = note == null || note.isBlank() ? null : note.trim();
        s.createdAt = LocalDateTime.now();
        return toDto(statements.save(s));
    }

    public Long fileIdOf(Long id) {
        return statements.findById(id).orElseThrow(() -> ApiException.notFound("Çıxarış tapılmadı")).fileId;
    }

    @Transactional
    public void delete(Long id) {
        E.AccountStatement s = statements.findById(id).orElseThrow(() -> ApiException.notFound("Çıxarış tapılmadı"));
        if (movements.existsByStatementId(id))
            throw ApiException.conflict("cixaris_istifade_olunub", "Bu çıxarışa bağlı hərəkətlər var, silmək olmaz");
        statements.delete(s);
        statements.flush();
        files.delete(s.fileId);
    }
}
